package com.toir.service.sparepartlifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.sparepartlifecycle.AppliedLifeRuleSnapshot;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleAggregateResponse;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleItem;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleItem.LinkedWorkOrderSummary;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleSummary;
import com.toir.dto.sparepartlifecycle.SparePartNextRequiredActionCandidate;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.entity.sparepartlifecycle.SparePartDueEventWorkOrderLink;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleView;
import com.toir.enums.sparepartlifecycle.SparePartWarehouseVisibility;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.sparepartlifecycle.SparePartDueEventRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import com.toir.repository.sparepartlifecycle.SparePartDueEventWorkOrderLinkRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SparePartLifecycleAggregateService {

    private final EquipmentRepository equipmentRepository;
    private final SparePartInstallationRepository installationRepository;
    private final SparePartDueEventRepository dueEventRepository;
    private final SparePartRepository sparePartRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final ScopeAccessService scopeAccessService;
    private final SparePartLifecyclePolicy lifecyclePolicy;
    private final SparePartDueEventWorkOrderLinkRepository linkRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SparePartLifecycleAggregateResponse get(UUID equipmentId, SparePartLifecycleView view,
                                                    String search, String status, int page, int size) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(), equipment.getDepartmentId());

        List<SparePartInstallation> all = installationRepository
                .findAllByEquipmentIdAndIsDeletedFalseOrderByInstalledAtDesc(equipmentId);
        List<UUID> installationIds = all.stream().map(SparePartInstallation::getId).toList();
        List<SparePartDueEvent> events = installationIds.isEmpty() ? List.of()
                : dueEventRepository.findAllByInstallationIdInAndIsDeletedFalse(installationIds);
        Map<UUID, SparePartDueEvent> currentEvent = currentEvents(events);
        Map<UUID, List<SparePartDueEventWorkOrderLink>> linksByEvent = events.isEmpty() ? Map.of()
                : linkRepository.findAllByDueEventIdInAndIsDeletedFalseOrderByCreatedAtDesc(
                        events.stream().map(SparePartDueEvent::getId).toList()).stream()
                .collect(Collectors.groupingBy(SparePartDueEventWorkOrderLink::getDueEventId));
        Set<UUID> sparePartIds = all.stream().map(SparePartInstallation::getSparePartId).collect(Collectors.toSet());
        Map<UUID, SparePart> parts = sparePartIds.isEmpty() ? Map.of()
                : sparePartRepository.findAllByIdInAndIsDeletedFalse(sparePartIds).stream()
                .collect(Collectors.toMap(SparePart::getId, Function.identity()));

        boolean stockVisible = hasPermission(PermissionConstants.STOCK_READ);
        Map<UUID, BigDecimal> availability = stockVisible
                ? warehouseAvailability(sparePartIds)
                : Map.of();
        SparePartLifecyclePolicy.Assessment assessment = lifecyclePolicy.assess(all, events);
        Set<UUID> activeInstallationIds = all.stream()
                .filter(installation -> installation.getStatus() == SparePartInstallationStatus.ACTIVE)
                .map(SparePartInstallation::getId)
                .collect(Collectors.toSet());
        SparePartNextRequiredActionCandidate nearest = nearestAction(events, activeInstallationIds);

        List<SparePartLifecycleItem> filtered = all.stream()
                .filter(installation -> matchesView(installation, currentEvent.get(installation.getId()), view))
                .map(installation -> item(installation, parts.get(installation.getSparePartId()),
                        currentEvent.get(installation.getId()), linksByEvent, stockVisible, availability))
                .filter(item -> matchesSearch(item, search))
                .filter(item -> status == null || status.isBlank()
                        || item.status() != null && item.status().name().equalsIgnoreCase(status.trim()))
                .toList();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        Page<SparePartLifecycleItem> items = new PageImpl<>(
                filtered.subList(from, to), PageRequest.of(safePage, safeSize), filtered.size());
        SparePartLifecycleSummary summary = new SparePartLifecycleSummary(
                equipmentId, assessment.readinessStatus(), assessment.hasEvaluationError(),
                assessment.evaluationErrorCount(), assessment.installedCount(), assessment.attentionCount(),
                assessment.warningCount(), assessment.maintenanceRequiredCount(), assessment.blockedCount(),
                assessment.acknowledgedAttentionCount(), nearest, Instant.now(),
                stockVisible ? SparePartWarehouseVisibility.VISIBLE
                        : SparePartWarehouseVisibility.HIDDEN_NO_PERMISSION);
        return new SparePartLifecycleAggregateResponse(summary, items);
    }

    private Map<UUID, BigDecimal> warehouseAvailability(Set<UUID> sparePartIds) {
        if (sparePartIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> totals = new HashMap<>();
        for (WarehouseStock stock : warehouseStockRepository
                .findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(sparePartIds)) {
            totals.merge(stock.getSparePartId(), BigDecimal.valueOf(stock.getAvailable()), BigDecimal::add);
        }
        return totals;
    }

    private Map<UUID, SparePartDueEvent> currentEvents(List<SparePartDueEvent> events) {
        Map<UUID, SparePartDueEvent> result = new LinkedHashMap<>();
        for (SparePartDueEvent event : events) {
            result.merge(event.getInstallationId(), event,
                    (left, right) -> eventRank(right) > eventRank(left) ? right : left);
        }
        return result;
    }

    private boolean matchesView(SparePartInstallation installation, SparePartDueEvent event,
                                SparePartLifecycleView requestedView) {
        SparePartLifecycleView view = requestedView == null ? SparePartLifecycleView.INSTALLED : requestedView;
        return switch (view) {
            case INSTALLED -> installation.getStatus() == SparePartInstallationStatus.ACTIVE;
            case ATTENTION -> installation.getStatus() == SparePartInstallationStatus.ACTIVE
                    && event != null && lifecyclePolicy.isAttention(event);
            case HISTORY -> installation.getStatus() != SparePartInstallationStatus.ACTIVE;
        };
    }

    private SparePartLifecycleItem item(SparePartInstallation installation, SparePart part,
                                        SparePartDueEvent event,
                                        Map<UUID, List<SparePartDueEventWorkOrderLink>> linksByEvent,
                                        boolean stockVisible,
                                        Map<UUID, BigDecimal> availability) {
        List<String> actions = new ArrayList<>();
        if (event != null && event.getAcknowledgedAt() == null
                && hasPermission(PermissionConstants.SPARE_PART_DUE_ACKNOWLEDGE)) {
            actions.add("ACKNOWLEDGE");
        }
        if (event != null && hasPermission(PermissionConstants.SPARE_PART_DUE_WORK_ORDER_CREATE)) {
            actions.add("CREATE_WORK_ORDER");
        }
        if (installation.getStatus() == SparePartInstallationStatus.ACTIVE
                && isManualRule(installation)
                && installation.getManualDueAt() == null
                && hasPermission(PermissionConstants.SPARE_PART_MANUAL_DUE)) {
            actions.add("MANUAL_DUE");
        }
        if (installation.getStatus() == SparePartInstallationStatus.ACTIVE
                && hasPermission(PermissionConstants.SPARE_PART_REPLACE)) {
            actions.add("REPLACE");
        }
        if (installation.getStatus() == SparePartInstallationStatus.ACTIVE
                && hasPermission(PermissionConstants.SPARE_PART_REMOVE)) {
            actions.add("REMOVE");
        }
        List<LinkedWorkOrderSummary> linked = event == null ? List.of()
                : linksByEvent.getOrDefault(event.getId(), List.of()).stream()
                .map(link -> new LinkedWorkOrderSummary(
                        link.getWorkOrderId(), link.getLinkStatus(), link.getCreatedAt()))
                .toList();
        if (linked.isEmpty() && event != null && event.getLinkedWorkOrderId() != null) {
            linked = List.of(new LinkedWorkOrderSummary(
                    event.getLinkedWorkOrderId(), "LEGACY_LINK", event.getUpdatedAt()));
        }
        return new SparePartLifecycleItem(
                installation.getId(), installation.getEquipmentId(), installation.getSparePartId(),
                part == null ? null : part.getName(), part == null ? null : part.getCode(),
                installation.getSerialNumberSnapshot(), part == null ? null : part.getManufacturer(), null,
                installation.getStatus() == SparePartInstallationStatus.ACTIVE,
                installation.getInstalledAt(), installation.getRemovedAt(),
                installation.getAppliedLifeRuleId() == null ? "NONE" : "SNAPSHOT",
                null, null, event == null ? null : event.getCurrentMeterValue(),
                event == null || event.getCurrentMeterValue() == null || event.getDueMeterValue() == null
                        ? null : event.getDueMeterValue().subtract(event.getCurrentMeterValue()),
                canonicalState(installation, event),
                installation.getLifecycleEvaluationState() == SparePartLifecycleEvaluationState.ERROR,
                event != null && event.getAcknowledgedAt() != null,
                event == null ? null : event.getAcknowledgedBy(),
                event == null ? null : event.getAcknowledgedAt(),
                event == null ? null : event.getDueAction(), event == null ? null : event.getId(),
                event == null ? null : event.getState(), linked, List.copyOf(actions),
                stockVisible ? availability.getOrDefault(installation.getSparePartId(), BigDecimal.ZERO) : null);
    }

    private SparePartLifecycleEvaluationState canonicalState(SparePartInstallation installation,
                                                              SparePartDueEvent event) {
        if (event == null) {
            return installation.getLifecycleEvaluationState();
        }
        return switch (event.getState()) {
            case UPCOMING, WARNING -> SparePartLifecycleEvaluationState.WARNING;
            case DUE -> SparePartLifecycleEvaluationState.DUE;
            case OVERDUE -> SparePartLifecycleEvaluationState.OVERDUE;
            case RESOLVED -> SparePartLifecycleEvaluationState.OK;
        };
    }

    private boolean matchesSearch(SparePartLifecycleItem item, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return contains(item.name(), term) || contains(item.code(), term) || contains(item.serialNumber(), term);
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private SparePartNextRequiredActionCandidate nearestAction(
            List<SparePartDueEvent> events, Set<UUID> activeInstallationIds) {
        return events.stream()
                .filter(event -> activeInstallationIds.contains(event.getInstallationId()))
                .filter(event -> event.getState() != SparePartDueEventState.RESOLVED)
                .min(Comparator.comparing(SparePartDueEvent::getDueAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(event -> new SparePartNextRequiredActionCandidate(
                        "SPARE_PART_DUE_EVENT", event.getId(), normalizeState(event.getState()).name(),
                        event.getDueAt(), event.getMeterType(), event.getDueMeterValue(), event.getCurrentMeterValue(),
                        event.getDueMeterValue() == null || event.getCurrentMeterValue() == null ? null
                                : event.getDueMeterValue().subtract(event.getCurrentMeterValue()),
                        null, event.getDueAction().name(), "SPARE_PART_LIFECYCLE_ATTENTION"))
                .orElse(null);
    }

    private SparePartDueEventState normalizeState(SparePartDueEventState state) {
        return state == SparePartDueEventState.UPCOMING ? SparePartDueEventState.WARNING : state;
    }

    private int eventRank(SparePartDueEvent event) {
        int stateRank = switch (event.getState()) {
            case OVERDUE -> 4;
            case DUE -> 3;
            case UPCOMING, WARNING -> 2;
            case RESOLVED -> 0;
        };
        int actionRank = switch (event.getDueAction()) {
            case BLOCK_OPERATION -> 3;
            case MAINTENANCE_REQUIRED -> 2;
            case WARNING_ONLY -> 1;
        };
        return stateRank * 10 + actionRank;
    }

    private boolean hasPermission(String permission) {
        return scopeAccessService.hasAuthority(PermissionConstants.WILDCARD)
                || scopeAccessService.hasAuthority(permission);
    }

    private boolean isManualRule(SparePartInstallation installation) {
        if (installation.getAppliedRuleSnapshot() == null
                || installation.getAppliedRuleSnapshot().isBlank()) {
            return false;
        }
        try {
            return objectMapper.readValue(
                    installation.getAppliedRuleSnapshot(), AppliedLifeRuleSnapshot.class
            ).combinationMode() == com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode.MANUAL;
        } catch (JsonProcessingException exception) {
            return false;
        }
    }
}
