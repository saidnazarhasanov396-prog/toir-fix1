package com.toir.service.sparepartlifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.sparepartlifecycle.AppliedLifeRuleSnapshot;
import com.toir.dto.sparepartlifecycle.CurrentMeterValue;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluation;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluationInput;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.entity.sparepartlifecycle.SparePartInstallationMeterBaseline;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.exception.SparePartLifecycleErrorCodes;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.util.AuditBuilderService;
import com.toir.repository.sparepartlifecycle.SparePartInstallationMeterBaselineRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Re-evaluates active installations exclusively from their immutable rule snapshots and captured baselines. */
@Service
@RequiredArgsConstructor
public class SparePartLifecycleEvaluationService {

    private final SparePartInstallationRepository installationRepository;
    private final SparePartInstallationMeterBaselineRepository baselineRepository;
    private final EquipmentMeterRepository meterRepository;
    private final SparePartLifecycleEvaluator evaluator;
    private final SparePartDueEventService dueEventService;
    private final ObjectMapper objectMapper;
    private final EquipmentRepository equipmentRepository;
    private final ScopeAccessService scopeAccessService;
    private final AuditBuilderService auditBuilderService;

    @Transactional
    public SparePartLifecycleEvaluation reevaluate(UUID installationId, Instant evaluatedAt) {
        SparePartInstallation installation = installationRepository
                .findByIdAndIsDeletedFalseForUpdate(installationId)
                .orElseThrow(() -> RestException.notFound("Spare-part installation not found: " + installationId));
        if (installation.getStatus() != SparePartInstallationStatus.ACTIVE) {
            throw RestException.conflict("INSTALLATION_NOT_ACTIVE: only an active installation can be re-evaluated");
        }
        Instant at = evaluatedAt == null ? Instant.now() : evaluatedAt;
        AppliedLifeRuleSnapshot snapshot = readSnapshot(installation);
        List<SparePartInstallationMeterBaseline> baselines = baselineRepository
                .findAllByInstallationIdAndIsDeletedFalse(installation.getId());
        Map<UUID, BigDecimal> baselineValues = new LinkedHashMap<>();
        for (SparePartInstallationMeterBaseline baseline : baselines) {
            baselineValues.put(baseline.getEquipmentMeterId(), baseline.getBaselineValue());
        }
        List<UUID> meterIds = baselineValues.keySet().stream().toList();
        Map<UUID, CurrentMeterValue> currentMeters = new LinkedHashMap<>();
        for (EquipmentMeter meter : meterIds.isEmpty()
                ? List.<EquipmentMeter>of()
                : meterRepository.findAllByIdInAndIsDeletedFalse(meterIds)) {
            currentMeters.put(meter.getId(), new CurrentMeterValue(
                    BigDecimal.valueOf(meter.getCurrentValue()),
                    meter.isActive(),
                    meter.getRolloverValue() == null ? null : BigDecimal.valueOf(meter.getRolloverValue())
            ));
        }
        SparePartLifecycleEvaluation evaluation = evaluator.evaluate(new SparePartLifecycleEvaluationInput(
                installation.getId(),
                installation.getInstalledAt(),
                snapshot,
                baselineValues,
                currentMeters,
                at,
                installation.getManualDueAt() != null
        ));
        installation.setLifecycleEvaluationState(evaluation.aggregateState());
        installation.setNextCalendarDueAt(evaluation.nextCalendarDueAt());
        installation.setLastEvaluatedAt(at);
        installation.setEvaluationDetails(writeJson(evaluation));
        installationRepository.save(installation);
        dueEventService.applyEvaluation(installation, evaluation);
        return evaluation;
    }

    @Transactional
    public SparePartLifecycleEvaluation markManualDue(UUID installationId, UUID actorId, String reason) {
        if (actorId == null) {
            throw RestException.badRequest("ACTOR_REQUIRED: actor is required");
        }
        if (!scopeAccessService.hasAuthority(PermissionConstants.WILDCARD)
                && !scopeAccessService.hasAuthority(PermissionConstants.SPARE_PART_MANUAL_DUE)) {
            throw RestException.forbidden("SPARE_PART_MANUAL_DUE permission is required");
        }
        if (reason == null || reason.isBlank()) {
            throw RestException.badRequest(
                    "Manual due reason is required", SparePartLifecycleErrorCodes.MANUAL_DUE_REASON_MISSING);
        }
        SparePartInstallation installation = installationRepository
                .findByIdAndIsDeletedFalseForUpdate(installationId)
                .orElseThrow(() -> RestException.notFound("Spare-part installation not found: " + installationId));
        if (installation.getStatus() != SparePartInstallationStatus.ACTIVE) {
            throw RestException.conflict("INSTALLATION_NOT_ACTIVE: only an active installation can be marked due");
        }
        AppliedLifeRuleSnapshot snapshot = readSnapshot(installation);
        if (snapshot.combinationMode()
                != com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode.MANUAL) {
            throw RestException.conflict(
                    "Only a MANUAL lifecycle rule can be marked due",
                    SparePartLifecycleErrorCodes.MANUAL_DUE_NOT_ALLOWED);
        }
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(installation.getEquipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + installation.getEquipmentId()));
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(), equipment.getDepartmentId());
        if (installation.getManualDueAt() == null) {
            installation.setManualDueAt(Instant.now());
            installation.setManualDueBy(actorId);
            installation.setManualDueReason(reason.trim());
            installationRepository.save(installation);
            auditBuilderService.log(
                    "spare_part_installation", installation.getId().toString(), AuditAction.UPDATE,
                    AuditModule.SPARE_PART, "Manual spare-part due action", null, installation);
        }
        return reevaluate(installationId, installation.getManualDueAt());
    }

    @Transactional
    public List<SparePartLifecycleEvaluation> reevaluateAllActive(Instant evaluatedAt) {
        return reevaluateInstallations(
                installationRepository.findAllByStatusAndIsDeletedFalseOrderByInstalledAtAsc(
                        SparePartInstallationStatus.ACTIVE),
                evaluatedAt
        );
    }

    @Transactional
    public List<SparePartLifecycleEvaluation> reevaluateForMeter(UUID equipmentMeterId, Instant evaluatedAt) {
        List<UUID> installationIds = baselineRepository
                .findAllByEquipmentMeterIdAndIsDeletedFalse(equipmentMeterId)
                .stream()
                .map(SparePartInstallationMeterBaseline::getInstallationId)
                .distinct()
                .toList();
        if (installationIds.isEmpty()) {
            return List.of();
        }
        return reevaluateInstallations(
                installationRepository.findAllByIdInAndStatusAndIsDeletedFalse(
                        installationIds, SparePartInstallationStatus.ACTIVE),
                evaluatedAt
        );
    }

    private List<SparePartLifecycleEvaluation> reevaluateInstallations(
            Collection<SparePartInstallation> installations,
            Instant evaluatedAt) {
        return installations.stream()
                .map(installation -> reevaluate(installation.getId(), evaluatedAt))
                .toList();
    }

    private AppliedLifeRuleSnapshot readSnapshot(SparePartInstallation installation) {
        if (installation.getAppliedRuleSnapshot() == null || installation.getAppliedRuleSnapshot().isBlank()) {
            throw RestException.conflict("RULE_SNAPSHOT_REQUIRED: installation has no applied life-rule snapshot");
        }
        try {
            return objectMapper.readValue(installation.getAppliedRuleSnapshot(), AppliedLifeRuleSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw RestException.conflict("RULE_SNAPSHOT_INVALID: installation rule snapshot cannot be evaluated");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize spare-part lifecycle evaluation", exception);
        }
    }
}
