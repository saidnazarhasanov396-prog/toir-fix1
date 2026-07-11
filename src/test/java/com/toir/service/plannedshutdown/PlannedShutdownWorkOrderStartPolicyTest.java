package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.PlannedShutdownReadinessAssessment;
import com.toir.dto.workorder.WorkOrderMaterialReadinessDto;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.MaterialReadinessStatus;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.service.PlannedShutdownService;
import com.toir.service.WorkOrderAssignmentEligibilityService;
import com.toir.service.WorkOrderMaterialReadinessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class PlannedShutdownWorkOrderStartPolicyTest {
    private final PlannedShutdownRepository shutdowns = mock(PlannedShutdownRepository.class);
    private final PlannedShutdownWorkItemRepository items = mock(PlannedShutdownWorkItemRepository.class);
    private final PlannedShutdownService shutdownService = mock(PlannedShutdownService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<PlannedShutdownService> shutdownServiceProvider = mock(ObjectProvider.class);
    private final WorkOrderMaterialReadinessService materials = mock(WorkOrderMaterialReadinessService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<WorkOrderAssignmentEligibilityService> assignments = mock(ObjectProvider.class);
    private final PlannedShutdownWorkOrderStartPolicy policy =
            new PlannedShutdownWorkOrderStartPolicy(shutdowns, items, shutdownServiceProvider, materials, assignments);

    PlannedShutdownWorkOrderStartPolicyTest() {
        lenient().when(shutdownServiceProvider.getObject()).thenReturn(shutdownService);
    }

    @Test
    void flaggedWorkWithoutShutdownFailsClosed() {
        WorkOrder workOrder = flagged();
        assertThatThrownBy(() -> policy.assertCanStart(workOrder))
                .isInstanceOf(RestException.class).hasMessageContaining("PLANNED_SHUTDOWN_LINK_REQUIRED");
    }

    @Test
    void ordinaryWorkOrderRemainsCompatible() {
        WorkOrder workOrder = new WorkOrder();
        policy.assertCanStart(workOrder);
        verifyNoInteractions(shutdowns, items, materials, assignments);
    }

    @Test
    void shutdownMustBeActiveAndInsideEffectiveWindow() {
        WorkOrder workOrder = flagged();
        UUID shutdownId = UUID.randomUUID();
        workOrder.setPlannedShutdownId(shutdownId);
        workOrder.setShutdownWorkItemId(UUID.randomUUID());
        PlannedShutdown shutdown = shutdown(shutdownId, PlannedShutdownStatus.APPROVED,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        when(shutdowns.findByIdAndIsDeletedFalse(shutdownId)).thenReturn(Optional.of(shutdown));
        stubLinkedItem(workOrder, shutdownId);
        assertThatThrownBy(() -> policy.assertCanStart(workOrder))
                .hasMessageContaining("PLANNED_SHUTDOWN_STATUS_INELIGIBLE");
    }

    @Test
    void currentSafeStateEvidenceAndEligibleAssignmentAreRequired() {
        WorkOrder workOrder = flagged();
        UUID shutdownId = UUID.randomUUID();
        workOrder.setPlannedShutdownId(shutdownId);
        workOrder.setShutdownWorkItemId(UUID.randomUUID());
        PlannedShutdown shutdown = shutdown(shutdownId, PlannedShutdownStatus.SAFE_STATE,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        when(shutdowns.findByIdAndIsDeletedFalse(shutdownId)).thenReturn(Optional.of(shutdown));
        stubLinkedItem(workOrder, shutdownId);
        when(shutdownService.assessSafeState(shutdownId, null))
                .thenReturn(new PlannedShutdownReadinessAssessment(true, List.of()));
        when(materials.getReadiness(workOrder.getId())).thenReturn(new WorkOrderMaterialReadinessDto(
                workOrder.getId(), null, MaterialReadinessStatus.READY, false, Instant.now(), List.of()));
        WorkOrderAssignmentEligibilityService assignment = mock(WorkOrderAssignmentEligibilityService.class);
        when(assignments.getObject()).thenReturn(assignment);
        when(assignment.isCurrentlyEligible(workOrder)).thenReturn(false);
        assertThatThrownBy(() -> policy.assertCanStart(workOrder))
                .hasMessageContaining("WORK_ORDER_ASSIGNMENT_INELIGIBLE");
    }

    private static WorkOrder flagged() {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setRequiresShutdown(true);
        return workOrder;
    }

    private static PlannedShutdown shutdown(UUID id, PlannedShutdownStatus status, Instant start, Instant end) {
        PlannedShutdown shutdown = new PlannedShutdown();
        shutdown.setId(id);
        shutdown.setStatus(status);
        shutdown.setApprovedStartAt(start);
        shutdown.setApprovedEndAt(end);
        return shutdown;
    }

    private void stubLinkedItem(WorkOrder workOrder, UUID shutdownId) {
        PlannedShutdownWorkItem item = new PlannedShutdownWorkItem();
        item.setId(workOrder.getShutdownWorkItemId());
        item.setPlannedShutdownId(shutdownId);
        item.setEquipmentId(workOrder.getEquipmentId());
        when(items.findByIdAndPlannedShutdownIdAndIsDeletedFalse(workOrder.getShutdownWorkItemId(), shutdownId))
                .thenReturn(Optional.of(item));
    }
}
