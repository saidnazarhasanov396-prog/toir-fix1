package com.toir.service.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.dto.pprplanning.PprPlanningSelectionRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.entity.planning.PprPlanningSession;
import com.toir.entity.planning.PprPlanningVariant;
import com.toir.entity.planning.PprPlanningVariantItem;
import com.toir.enums.PprScopeType;
import com.toir.enums.planning.PprPlanningSessionStatus;
import com.toir.enums.planning.PprPlanningVariantStatus;
import com.toir.exception.RestException;
import com.toir.repository.planning.PprPlanningSessionRepository;
import com.toir.repository.planning.PprPlanningVariantItemRepository;
import com.toir.repository.planning.PprPlanningVariantRepository;
import com.toir.service.maintanance.MaintenanceScheduleService;
import com.toir.service.maintanance.MaintenanceScheduleSnapshotDraftFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PprPlanningVariantService {

    private final PprPlanningSessionRepository sessionRepository;
    private final PprPlanningVariantRepository variantRepository;
    private final PprPlanningVariantItemRepository itemRepository;
    private final MaintenanceScheduleService scheduleService;
    private final MaintenanceScheduleSnapshotDraftFactory snapshotDraftFactory;
    private final PprVariantContentHasher contentHasher;
    private final ObjectMapper objectMapper;

    @Transactional
    public PprPlanningVariant calculate(
            UUID sessionId,
            UUID variantId,
            MaintenanceScheduleCalculationRequest request) {
        PprPlanningSession session = sessionRepository
                .findByIdAndIsDeletedFalseForUpdate(sessionId)
                .orElseThrow(() -> RestException.notFound(
                        "PPR planning session not found: " + sessionId));
        PprPlanningVariant variant = variantRepository
                .findByIdAndSessionIdAndIsDeletedFalse(variantId, sessionId)
                .orElseThrow(() -> RestException.notFound(
                        "PPR planning variant not found: " + variantId));
        requireMutable(session);
        requireSessionBoundary(session, request);

        MaintenanceSchedulePreviewResponse preview =
                scheduleService.preview(request.toPreviewRequest());
        if (preview.diagnostics().stream()
                .anyMatch(item -> "BLOCKING".equalsIgnoreCase(item.severity()))) {
            throw RestException.badRequest(
                    "PPR planning variant has blocking diagnostics");
        }
        if (preview.items() == null || preview.items().isEmpty()) {
            throw RestException.badRequest(
                    "PPR planning variant has no maintenance occurrences");
        }

        long nextRevision;
        try {
            nextRevision = Math.addExact(variant.getRevision(), 1L);
        } catch (ArithmeticException exception) {
            throw conflict(
                    "PPR_PLANNING_VARIANT_REVISION_OVERFLOW",
                    "PPR planning variant revision overflow");
        }

        PprPlan draftPlan = draftPlan(session, request);
        List<MaintenanceScheduleCalculationItem> calculationItems =
                snapshotDraftFactory.create(draftPlan, nextRevision, preview.items());
        List<PprPlanningVariantItem> variantItems = calculationItems.stream()
                .map(item -> toVariantItem(variant, nextRevision, item))
                .toList();
        String contentHash = contentHasher.compute(request, variantItems);

        itemRepository.saveAll(variantItems);
        variant.setRevision(nextRevision);
        variant.setHashVersion(PprVariantContentHasher.VERSION);
        variant.setContentHash(contentHash);
        variant.setInputsJson(writeInputs(request));
        variant.setTaskCount(variantItems.size());
        variant.setTotalLaborHours(variantItems.stream()
                .map(PprPlanningVariantItem::getNormativeLaborHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        variant.setTotalDowntimeMinutes(variantItems.stream()
                .mapToLong(item -> Duration.between(
                        item.getScheduledStart(), item.getScheduledEnd()).toMinutes())
                .sum());
        variant.setFirstPlannedDate(variantItems.stream()
                .map(PprPlanningVariantItem::getPlannedDate)
                .min(java.time.LocalDate::compareTo)
                .orElse(null));
        variant.setLastPlannedDate(variantItems.stream()
                .map(PprPlanningVariantItem::getPlannedDate)
                .max(java.time.LocalDate::compareTo)
                .orElse(null));
        variant.setStatus(PprPlanningVariantStatus.CALCULATED);

        if (variantId.equals(session.getSelectedVariantId())) {
            session.setSelectedVariantId(null);
        }
        clearStaleApprovalBinding(session);
        session.setStatus(PprPlanningSessionStatus.READY_FOR_SELECTION);
        sessionRepository.save(session);
        return variantRepository.saveAndFlush(variant);
    }

    @Transactional
    public PprPlanningSession select(
            UUID sessionId,
            UUID variantId,
            PprPlanningSelectionRequest request) {
        if (request == null) {
            throw RestException.badRequest("Selection payload is required");
        }
        if (!variantId.equals(request.variantId())) {
            throw conflict(
                    "PPR_PLANNING_SELECTION_STALE",
                    "Selected variant id does not match the request");
        }
        PprPlanningSession session = sessionRepository
                .findByIdAndIsDeletedFalseForUpdate(sessionId)
                .orElseThrow(() -> RestException.notFound(
                        "PPR planning session not found: " + sessionId));
        requireMutable(session);
        PprPlanningVariant selected = variantRepository
                .findByIdAndSessionIdAndIsDeletedFalse(variantId, sessionId)
                .orElseThrow(() -> RestException.notFound(
                        "PPR planning variant not found: " + variantId));
        if (selected.getStatus() != PprPlanningVariantStatus.CALCULATED
                && selected.getStatus() != PprPlanningVariantStatus.SELECTED
                || selected.getRevision() != request.revision()
                || !Objects.equals(selected.getContentHash(), request.contentHash())) {
            throw conflict(
                    "PPR_PLANNING_SELECTION_STALE",
                    "Selected variant revision or hash is stale");
        }

        List<PprPlanningVariant> variants =
                variantRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId);
        variants.forEach(variant -> variant.setStatus(variant.getId().equals(variantId)
                ? PprPlanningVariantStatus.SELECTED
                : variant.getRevision() > 0
                        ? PprPlanningVariantStatus.NOT_SELECTED
                        : PprPlanningVariantStatus.DRAFT));
        variantRepository.saveAll(variants);
        session.setSelectedVariantId(variantId);
        session.setStatus(PprPlanningSessionStatus.SELECTED);
        return sessionRepository.save(session);
    }

    private static PprPlan draftPlan(
            PprPlanningSession session,
            MaintenanceScheduleCalculationRequest request) {
        PprPlan plan = new PprPlan();
        plan.setName(request.name().trim());
        plan.setNotes(request.notes());
        plan.setStartDate(session.getStartDate());
        plan.setEndDate(session.getEndDate());
        plan.setDepartmentId(session.getDepartmentId());
        plan.setScopeType(session.getDepartmentId() == null
                ? PprScopeType.ENTERPRISE
                : PprScopeType.DEPARTMENT);
        plan.setExcludedWeekdays(request.excludedWeekdays());
        return plan;
    }

    private static PprPlanningVariantItem toVariantItem(
            PprPlanningVariant variant,
            long revision,
            MaintenanceScheduleCalculationItem source) {
        PprPlanningVariantItem item = new PprPlanningVariantItem();
        item.setVariant(variant);
        item.setRevision(revision);
        item.setSourceItemKey(source.getSourceItemKey());
        item.setSourceItemKeyVersion(source.getSourceItemKeyVersion());
        item.setEquipmentId(source.getEquipmentId());
        item.setRegulationId(source.getRegulationId());
        item.setMaintenanceRuleId(source.getMaintenanceRuleId());
        item.setTemplateId(source.getTemplateId());
        item.setMaintenanceType(source.getMaintenanceType());
        item.setTriggerType(source.getTriggerType());
        item.setTriggerDiscriminator(source.getTriggerDiscriminator());
        item.setCycleOrdinal(source.getCycleOrdinal());
        item.setPlannedDate(source.getPlannedDate());
        item.setScheduledStart(source.getScheduledStart());
        item.setScheduledEnd(source.getScheduledEnd());
        item.setDueDate(source.getDueDate());
        item.setNormativeLaborHours(source.getNormativeLaborHours());
        item.setPriority(source.getPriority());
        item.setDepartmentId(source.getDepartmentId());
        item.setEquipmentCodeSnapshot(source.getEquipmentCodeSnapshot());
        item.setEquipmentNameSnapshot(source.getEquipmentNameSnapshot());
        item.setRegulationNameSnapshot(source.getRegulationNameSnapshot());
        item.setMaintenanceRuleNameSnapshot(source.getMaintenanceRuleNameSnapshot());
        item.setTemplateNameSnapshot(source.getTemplateNameSnapshot());
        item.setTaskTitleSnapshot(source.getTaskTitleSnapshot());
        item.setWorkOrderLeadDays(source.getWorkOrderLeadDays() == null
                ? 7
                : source.getWorkOrderLeadDays());
        item.setRequiredEvidenceTypes(source.getRequiredEvidenceTypes() == null
                ? java.util.Set.of()
                : java.util.Set.copyOf(source.getRequiredEvidenceTypes()));
        return item;
    }

    private static void requireMutable(PprPlanningSession session) {
        if (session.getStatus() == PprPlanningSessionStatus.PENDING_APPROVAL
                || session.getStatus() == PprPlanningSessionStatus.APPROVED
                || session.getStatus() == PprPlanningSessionStatus.CANCELLED) {
            throw conflict(
                    "PPR_PLANNING_SESSION_IMMUTABLE",
                    "PPR planning session cannot be changed in its current state");
        }
    }

    private static void clearStaleApprovalBinding(PprPlanningSession session) {
        session.setSelectedVariantRevision(null);
        session.setSelectedVariantHash(null);
        session.setSelectedVariantHashVersion(null);
        session.setApprovalRequestId(null);
    }

    private static void requireSessionBoundary(
            PprPlanningSession session,
            MaintenanceScheduleCalculationRequest request) {
        if (request == null
                || !Objects.equals(session.getDepartmentId(), request.departmentId())
                || !Objects.equals(session.getStartDate(), request.fromDate())
                || !Objects.equals(session.getEndDate(), request.toDate())) {
            throw RestException.badRequest(
                    "Variant calculation must use the planning session boundary");
        }
    }

    private String writeInputs(MaintenanceScheduleCalculationRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize PPR planning variant inputs",
                    exception);
        }
    }

    private static RestException conflict(String code, String message) {
        return new RestException(message, HttpStatus.CONFLICT, code);
    }
}
