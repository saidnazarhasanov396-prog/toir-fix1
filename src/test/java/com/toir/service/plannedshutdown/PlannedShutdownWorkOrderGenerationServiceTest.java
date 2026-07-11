package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.PlannedShutdownWorkOrderGenerationRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import java.time.Instant;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.service.WorkOrderService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlannedShutdownWorkOrderGenerationServiceTest {
    private final PlannedShutdownRepository shutdowns = mock(PlannedShutdownRepository.class);
    private final PlannedShutdownWorkItemRepository items = mock(PlannedShutdownWorkItemRepository.class);
    private final WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
    private final WorkOrderService workOrderService = mock(WorkOrderService.class);
    private final PlannedShutdownWorkOrderGenerationService service =
            new PlannedShutdownWorkOrderGenerationService(shutdowns, items, workOrders, workOrderService);

    @Test
    void requiresIdempotencyKeyBeforeMutation() {
        assertThatThrownBy(() -> service.generate(UUID.randomUUID(), new PlannedShutdownWorkOrderGenerationRequest(List.of()), " "))
                .hasMessageContaining("IDEMPOTENCY_KEY_REQUIRED");
        verifyNoInteractions(shutdowns, items, workOrders, workOrderService);
    }

    @Test
    void ordersItemsDeterministicallyAndReplaysCanonicalWorkOrder() {
        UUID shutdownId = UUID.randomUUID();
        PlannedShutdown shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId);
        shutdown.setStatus(PlannedShutdownStatus.SAFE_STATE);
        shutdown.setScopeVersion(7L);
        when(shutdowns.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        PlannedShutdownWorkItem second = item(shutdownId, 2);
        PlannedShutdownWorkItem first = item(shutdownId, 1);
        when(items.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of(first, second));
        WorkOrder existing = new WorkOrder(); existing.setId(UUID.randomUUID());
        String firstKey = "PS:" + shutdownId + ":" + first.getId() + ":7";
        when(workOrders.findByGenerationKeyAndIsDeletedFalse(firstKey)).thenReturn(Optional.of(existing));
        WorkOrderDto dto = dto(existing.getId());
        when(workOrderService.findById(existing.getId())).thenReturn(dto);
        WorkOrderDto created = dto(UUID.randomUUID());
        when(workOrderService.create(any())).thenReturn(created);

        var result = service.generate(shutdownId, new PlannedShutdownWorkOrderGenerationRequest(List.of()), "request-1");

        assertThat(result.workOrders()).containsExactly(dto, created);
        verify(workOrders).lockGenerationKey(firstKey);
        verify(workOrders).lockGenerationKey("PS:" + shutdownId + ":" + second.getId() + ":7");
        verify(workOrderService, times(1)).create(argThat(request ->
                request.plannedShutdownId().equals(shutdownId)
                        && request.shutdownWorkItemId().equals(second.getId())
                        && request.generationKey().endsWith(":7")));
    }

    @Test
    void invalidSelectionIsRejectedBeforeAnyWorkOrderMutation() {
        UUID shutdownId = UUID.randomUUID();
        PlannedShutdown shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId); shutdown.setStatus(PlannedShutdownStatus.APPROVED); shutdown.setScopeVersion(3L);
        when(shutdowns.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        when(items.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of(item(shutdownId, 1)));

        assertThatThrownBy(() -> service.generate(shutdownId,
                new PlannedShutdownWorkOrderGenerationRequest(List.of(UUID.randomUUID())), "request-invalid"))
                .hasMessageContaining("INVALID_SHUTDOWN_WORK_ITEM_SELECTION");
        verifyNoInteractions(workOrders, workOrderService);
    }

    private static PlannedShutdownWorkItem item(UUID shutdownId, int order) {
        PlannedShutdownWorkItem item = new PlannedShutdownWorkItem();
        item.setId(UUID.randomUUID()); item.setPlannedShutdownId(shutdownId); item.setEquipmentId(UUID.randomUUID());
        item.setTitle("Item " + order); item.setPriority(PriorityLevel.MEDIUM); item.setOrderNumber(order);
        item.setRequiresShutdown(true); return item;
    }

    private static WorkOrderDto dto(UUID id) {
        return new WorkOrderDto(id, "WO-1", "Work", UUID.randomUUID(), UUID.randomUUID(),
                "Equipment", "Department", null, null, null, null, WorkOrderStatus.APPROVED,
                WorkOrderType.PLANNED, WorkType.REPAIR, PriorityLevel.MEDIUM, Instant.now(),
                Instant.now().plusSeconds(3600), null, null, null, null, null, null, null, null,
                null, null, List.of(), null, null, 0, 0);
    }
}
