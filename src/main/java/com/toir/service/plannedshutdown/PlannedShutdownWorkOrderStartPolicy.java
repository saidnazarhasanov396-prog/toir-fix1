package com.toir.service.plannedshutdown;

import com.toir.dto.workorder.WorkOrderMaterialReadinessDto;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.service.PlannedShutdownService;
import com.toir.service.WorkOrderAssignmentEligibilityService;
import com.toir.service.WorkOrderMaterialReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class PlannedShutdownWorkOrderStartPolicy {
    private static final EnumSet<PlannedShutdownStatus> STARTABLE = EnumSet.of(
            PlannedShutdownStatus.SAFE_STATE, PlannedShutdownStatus.REPAIR_IN_PROGRESS);

    private final PlannedShutdownRepository shutdownRepository;
    private final PlannedShutdownWorkItemRepository workItemRepository;
    private final ObjectProvider<PlannedShutdownService> shutdownServiceProvider;
    private final WorkOrderMaterialReadinessService materialReadinessService;
    private final ObjectProvider<WorkOrderAssignmentEligibilityService> assignmentEligibilityProvider;

    public void assertCanStart(WorkOrder workOrder) {
        if (workOrder == null || (!workOrder.isRequiresShutdown() && !workOrder.isRequiresIsolation())) return;
        if (workOrder.getPlannedShutdownId() == null || workOrder.getShutdownWorkItemId() == null) {
            throw RestException.conflict("PLANNED_SHUTDOWN_LINK_REQUIRED");
        }
        PlannedShutdown shutdown = shutdownRepository.findByIdAndIsDeletedFalse(workOrder.getPlannedShutdownId())
                .orElseThrow(() -> RestException.conflict("PLANNED_SHUTDOWN_INACTIVE"));
        var item = workItemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(
                        workOrder.getShutdownWorkItemId(), shutdown.getId())
                .orElseThrow(() -> RestException.conflict("SHUTDOWN_WORK_ITEM_LINK_INVALID"));
        if (!java.util.Objects.equals(item.getEquipmentId(), workOrder.getEquipmentId())) {
            throw RestException.conflict("SHUTDOWN_WORK_ITEM_EQUIPMENT_MISMATCH");
        }
        if (!STARTABLE.contains(shutdown.getLifecycleStatus())) {
            throw RestException.conflict("PLANNED_SHUTDOWN_STATUS_INELIGIBLE:" + shutdown.getLifecycleStatus());
        }
        Instant now = Instant.now();
        Instant end = shutdown.getEffectiveExtensionEndAt() == null
                ? shutdown.getApprovedEndAt() : shutdown.getEffectiveExtensionEndAt();
        if (shutdown.getApprovedStartAt() == null || end == null
                || now.isBefore(shutdown.getApprovedStartAt()) || now.isAfter(end)) {
            throw RestException.conflict("PLANNED_SHUTDOWN_WINDOW_INACTIVE");
        }
        var assessment = shutdownServiceProvider.getObject().assessSafeState(shutdown.getId(), null);
        if (!assessment.canProceed()) {
            String codes = assessment.blockers().stream().map(blocker -> blocker.code()).distinct().sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            throw RestException.conflict("PLANNED_SHUTDOWN_SAFETY_BLOCKED:" + codes);
        }
        WorkOrderMaterialReadinessDto materials = materialReadinessService.getReadiness(workOrder.getId());
        if (materials.blocking()) throw RestException.conflict("WORK_ORDER_CRITICAL_MATERIAL_DEFICIT");
        WorkOrderAssignmentEligibilityService assignments = assignmentEligibilityProvider.getObject();
        if (!assignments.isCurrentlyEligible(workOrder)) {
            throw RestException.conflict("WORK_ORDER_ASSIGNMENT_INELIGIBLE");
        }
    }
}
