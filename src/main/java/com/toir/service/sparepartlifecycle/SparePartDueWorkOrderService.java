package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.CreateDueEventWorkOrderRequest;
import com.toir.dto.sparepartlifecycle.DueEventWorkOrderActionResponse;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartDueEventWorkOrderLink;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.exception.RestException;
import com.toir.exception.SparePartLifecycleErrorCodes;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.sparepartlifecycle.SparePartDueEventRepository;
import com.toir.repository.sparepartlifecycle.SparePartDueEventWorkOrderLinkRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.service.WorkOrderService;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SparePartDueWorkOrderService {

    private static final Set<WorkOrderStatus> ACTIVE_STATUSES = Set.of(
            WorkOrderStatus.DRAFT, WorkOrderStatus.PLANNED, WorkOrderStatus.APPROVED,
            WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.SUSPENDED);

    private final SparePartDueEventRepository dueEventRepository;
    private final SparePartDueEventWorkOrderLinkRepository linkRepository;
    private final SparePartInstallationRepository installationRepository;
    private final EquipmentRepository equipmentRepository;
    private final ScopeAccessService scopeAccessService;
    private final WorkOrderService workOrderService;

    @Transactional
    public DueEventWorkOrderActionResponse create(UUID dueEventId, String idempotencyKey, UUID actorId,
                                                  CreateDueEventWorkOrderRequest request) {
        if (actorId == null) {
            throw RestException.badRequest("ACTOR_REQUIRED: actor is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw RestException.badRequest("Idempotency-Key is required");
        }
        if (!scopeAccessService.hasAuthority(PermissionConstants.WILDCARD)
                && !scopeAccessService.hasAuthority(PermissionConstants.SPARE_PART_DUE_WORK_ORDER_CREATE)) {
            throw RestException.forbidden("SPARE_PART_DUE_WORK_ORDER_CREATE permission is required");
        }
        SparePartDueEvent event = dueEventRepository.findByIdAndIsDeletedFalseForUpdate(dueEventId)
                .orElseThrow(() -> RestException.notFound("Spare-part due event not found: " + dueEventId));
        if (event.getState() == SparePartDueEventState.RESOLVED) {
            throw RestException.conflict("Resolved due event cannot create a work order",
                    SparePartLifecycleErrorCodes.DUE_EVENT_WORK_ORDER_CONFLICT);
        }
        SparePartInstallation installation = installationRepository.findByIdAndIsDeletedFalse(event.getInstallationId())
                .orElseThrow(() -> RestException.notFound("Spare-part installation not found: " + event.getInstallationId()));
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(installation.getEquipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + installation.getEquipmentId()));
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(), equipment.getDepartmentId());

        var replay = linkRepository.findByDueEventIdAndIdempotencyKeyAndIsDeletedFalse(
                dueEventId, idempotencyKey.trim());
        if (replay.isPresent()) {
            return new DueEventWorkOrderActionResponse(
                    dueEventId, workOrderService.findById(replay.get().getWorkOrderId()), true);
        }

        if (event.getLinkedWorkOrderId() != null) {
            WorkOrderDto existing = workOrderService.findById(event.getLinkedWorkOrderId());
            if (ACTIVE_STATUSES.contains(existing.status())) {
                return new DueEventWorkOrderActionResponse(dueEventId, existing, true);
            }
        }
        UUID departmentId = equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId() : equipment.getDepartmentId();
        String title = request == null || request.title() == null || request.title().isBlank()
                ? "Spare-part lifecycle maintenance" : request.title().trim();
        String reason = request == null || request.reason() == null ? null : request.reason().trim();
        String generationKey = "spare-part-due-event:" + dueEventId + ":" + idempotencyKey.trim();
        WorkOrderRequest workOrderRequest = new WorkOrderRequest(
                null, title, equipment.getId(), null, null, departmentId, null,
                null, null, null, null, null, null,
                WorkOrderType.EMERGENCY, WorkType.REPAIR, null, null, PriorityLevel.HIGH,
                null, null, actorId, reason, null, event.getCycleKey(), false, false,
                null, null, null, false, false, generationKey, null, null);
        WorkOrderDto created = workOrderService.createGenerated(workOrderRequest, actorId);
        SparePartDueEventWorkOrderLink link = new SparePartDueEventWorkOrderLink();
        link.setDueEventId(event.getId());
        link.setWorkOrderId(created.id());
        link.setIdempotencyKey(idempotencyKey.trim());
        link.setLinkStatus("CREATED");
        link.setLinkedBy(actorId);
        linkRepository.save(link);
        event.setLinkedWorkOrderId(created.id());
        dueEventRepository.save(event);
        return new DueEventWorkOrderActionResponse(dueEventId, created, false);
    }
}
