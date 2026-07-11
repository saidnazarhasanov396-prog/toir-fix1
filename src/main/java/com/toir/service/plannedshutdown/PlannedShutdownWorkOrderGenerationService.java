package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.PlannedShutdownWorkOrderGenerationRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownWorkOrderGenerationResponse;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlannedShutdownWorkOrderGenerationService {
    private static final EnumSet<PlannedShutdownStatus> GENERATION_ALLOWED = EnumSet.of(
            PlannedShutdownStatus.APPROVED, PlannedShutdownStatus.PREPARATION,
            PlannedShutdownStatus.SHUTDOWN_STARTED, PlannedShutdownStatus.SAFE_STATE,
            PlannedShutdownStatus.REPAIR_IN_PROGRESS);

    private final PlannedShutdownRepository shutdownRepository;
    private final PlannedShutdownWorkItemRepository workItemRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;

    @Transactional
    public PlannedShutdownWorkOrderGenerationResponse generate(UUID shutdownId,
            PlannedShutdownWorkOrderGenerationRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw RestException.badRequest("IDEMPOTENCY_KEY_REQUIRED: Idempotency-Key header is required");
        }
        PlannedShutdown shutdown = shutdownRepository.findByIdAndIsDeletedFalseForUpdate(shutdownId)
                .orElseThrow(() -> RestException.notFound("Planned shutdown not found: " + shutdownId));
        if (!GENERATION_ALLOWED.contains(shutdown.getLifecycleStatus())) {
            throw RestException.conflict("WORK_ORDER_GENERATION_NOT_ALLOWED:" + shutdown.getLifecycleStatus());
        }
        List<PlannedShutdownWorkItem> canonical = workItemRepository
                .findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId);
        Set<UUID> requested = request == null ? Set.of() : new HashSet<>(request.workItemIds());
        if (!requested.isEmpty()) {
            Set<UUID> known = canonical.stream().map(PlannedShutdownWorkItem::getId)
                    .collect(java.util.stream.Collectors.toSet());
            if (!known.containsAll(requested)) throw RestException.badRequest("INVALID_SHUTDOWN_WORK_ITEM_SELECTION");
            canonical = canonical.stream().filter(item -> requested.contains(item.getId())).toList();
        }
        if (canonical.isEmpty()) throw RestException.badRequest("SHUTDOWN_WORK_ITEMS_REQUIRED");
        long windowVersion = shutdown.getScopeVersion() == null ? 0L : shutdown.getScopeVersion();
        var generated = canonical.stream()
                .sorted(Comparator.comparing(PlannedShutdownWorkItem::getOrderNumber)
                        .thenComparing(PlannedShutdownWorkItem::getId))
                .map(item -> canonicalWorkOrder(shutdown, item, windowVersion))
                .toList();
        return new PlannedShutdownWorkOrderGenerationResponse(shutdownId, windowVersion, generated);
    }

    private com.toir.dto.workorder.WorkOrderDto canonicalWorkOrder(PlannedShutdown shutdown,
            PlannedShutdownWorkItem item, long windowVersion) {
        String key = "PS:" + shutdown.getId() + ":" + item.getId() + ":" + windowVersion;
        workOrderRepository.lockGenerationKey(key);
        return workOrderRepository.findByGenerationKeyAndIsDeletedFalse(key)
                .map(existing -> workOrderService.findById(existing.getId()))
                .orElseGet(() -> workOrderService.create(toRequest(shutdown, item, key)));
    }

    private WorkOrderRequest toRequest(PlannedShutdown shutdown, PlannedShutdownWorkItem item, String key) {
        return new WorkOrderRequest(null, item.getTitle(), item.getEquipmentId(), null, null,
                shutdown.getDepartmentId(), null, null, null, null, null, null, null,
                WorkOrderType.PLANNED, WorkType.REPAIR, null, null, item.getPriority(),
                shutdown.getApprovedStartAt(), shutdown.getEffectiveExtensionEndAt() == null
                        ? shutdown.getApprovedEndAt() : shutdown.getEffectiveExtensionEndAt(),
                null, "Planned shutdown " + shutdown.getCode(), null, null, false, false)
                .withGenerationKey(key)
                .withSafetyRequirements(item.isRequiresShutdown(), item.isRequiresIsolation())
                .withPlannedShutdown(shutdown.getId(), item.getId());
    }
}
