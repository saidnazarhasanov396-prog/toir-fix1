package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.PlannedShutdownWorkOrderGenerationRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.entity.plannedshutdown.PlannedShutdownGenerationRequest;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import java.time.Instant;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownGenerationRequestRepository;
import com.toir.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.dao.DataIntegrityViolationException;

class PlannedShutdownWorkOrderGenerationServiceTest {
    private final PlannedShutdownRepository shutdowns = mock(PlannedShutdownRepository.class);
    private final PlannedShutdownWorkItemRepository items = mock(PlannedShutdownWorkItemRepository.class);
    private final PlannedShutdownGenerationRequestRepository requests = mock(PlannedShutdownGenerationRequestRepository.class);
    private final WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
    private final WorkOrderService workOrderService = mock(WorkOrderService.class);
    private final PlannedShutdownWorkOrderGenerationService service =
            new PlannedShutdownWorkOrderGenerationService(shutdowns, items, requests, workOrders, workOrderService);

    @Test
    void requiresIdempotencyKeyBeforeMutation() {
        assertThatThrownBy(() -> service.generate(UUID.randomUUID(), new PlannedShutdownWorkOrderGenerationRequest(List.of()), " "))
                .hasMessageContaining("IDEMPOTENCY_KEY_REQUIRED");
        verifyNoInteractions(shutdowns, items, requests, workOrders, workOrderService);
    }

    @Test
    void ordersItemsDeterministicallyAndReplaysCanonicalWorkOrder() {
        UUID shutdownId = UUID.randomUUID();
        PlannedShutdown shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId);
        shutdown.setStatus(PlannedShutdownStatus.SAFE_STATE);
        shutdown.setDepartmentId(UUID.randomUUID());
        shutdown.setWindowVersion(7L);
        shutdown.setApprovedStartAt(Instant.parse("2026-07-11T10:00:00Z"));
        shutdown.setApprovedEndAt(Instant.parse("2026-07-11T12:00:00Z"));
        when(shutdowns.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        PlannedShutdownWorkItem second = item(shutdownId, 2);
        PlannedShutdownWorkItem first = item(shutdownId, 1);
        when(items.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of(first, second));
        WorkOrder existing = canonical(shutdown, first, 7L); existing.setId(UUID.randomUUID());
        String firstKey = "PS:" + shutdownId + ":" + first.getId() + ":7";
        when(workOrders.findByGenerationKeyAndIsDeletedFalse(firstKey)).thenReturn(Optional.of(existing));
        WorkOrderDto dto = dto(existing.getId());
        when(workOrderService.findById(existing.getId())).thenReturn(dto);
        WorkOrderDto created = dto(UUID.randomUUID());
        when(workOrderService.createGenerated(any())).thenReturn(created);

        var result = service.generate(shutdownId, new PlannedShutdownWorkOrderGenerationRequest(List.of()), "request-1");

        assertThat(result.workOrders()).containsExactly(dto, created);
        verify(workOrders).lockGenerationKey(firstKey);
        verify(workOrders).lockGenerationKey("PS:" + shutdownId + ":" + second.getId() + ":7");
        verify(workOrderService, times(1)).createGenerated(argThat(request ->
                request.plannedShutdownId().equals(shutdownId)
                        && request.shutdownWorkItemId().equals(second.getId())
                        && request.equipmentId().equals(second.getEquipmentId())
                        && request.departmentId().equals(shutdown.getDepartmentId())
                        && request.generationKey().endsWith(":7")
                        && request.requiresShutdown()));
        verify(requests).saveAndFlush(argThat(command -> command.getWindowVersion() == 7L
                && command.getOrderedWorkOrderIds().equals(dto.id() + "," + created.id())));
    }

    @Test
    void invalidSelectionIsRejectedBeforeAnyWorkOrderMutation() {
        UUID shutdownId = UUID.randomUUID();
        PlannedShutdown shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId); shutdown.setStatus(PlannedShutdownStatus.APPROVED); shutdown.setWindowVersion(3L);
        when(shutdowns.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        when(items.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of(item(shutdownId, 1)));

        assertThatThrownBy(() -> service.generate(shutdownId,
                new PlannedShutdownWorkOrderGenerationRequest(List.of(UUID.randomUUID())), "request-invalid"))
                .hasMessageContaining("INVALID_SHUTDOWN_WORK_ITEM_SELECTION");
        verifyNoInteractions(workOrders, workOrderService);
    }

    @Test
    void sameHeaderWithDifferentFingerprintIsConflictBeforeWorkOrderAccess() {
        UUID shutdownId = UUID.randomUUID();
        PlannedShutdown shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId); shutdown.setStatus(PlannedShutdownStatus.APPROVED); shutdown.setWindowVersion(4L);
        PlannedShutdownWorkItem item = item(shutdownId, 1);
        when(shutdowns.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        when(items.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId)).thenReturn(List.of(item));
        PlannedShutdownGenerationRequest recorded = new PlannedShutdownGenerationRequest();
        recorded.setRequestFingerprint("different"); recorded.setWindowVersion(3L);
        when(requests.findByPlannedShutdownIdAndIdempotencyKeyAndIsDeletedFalse(shutdownId, "same"))
                .thenReturn(Optional.of(recorded));

        assertThatThrownBy(() -> service.generate(shutdownId,
                new PlannedShutdownWorkOrderGenerationRequest(List.of()), "same"))
                .hasMessageContaining("IDEMPOTENCY_MISMATCH");
        verifyNoInteractions(workOrders, workOrderService);
    }

    @Test
    void exactGenerationConstraintMapsConflictWithoutPoisonedTransactionRequery() {
        UUID shutdownId = UUID.randomUUID();
        PlannedShutdown shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId); shutdown.setStatus(PlannedShutdownStatus.APPROVED); shutdown.setWindowVersion(2L);
        shutdown.setApprovedStartAt(Instant.now()); shutdown.setApprovedEndAt(Instant.now().plusSeconds(3600));
        PlannedShutdownWorkItem item = item(shutdownId, 1);
        when(shutdowns.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        when(items.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId)).thenReturn(List.of(item));
        when(workOrderService.createGenerated(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate constraint uq_work_orders_active_generation_key"));

        assertThatThrownBy(() -> service.generate(shutdownId,
                new PlannedShutdownWorkOrderGenerationRequest(List.of()), "constraint"))
                .hasMessageContaining("PLANNED_SHUTDOWN_GENERATION_KEY_CONFLICT");
        verify(workOrders, times(1)).findByGenerationKeyAndIsDeletedFalse(any());
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void unrelatedIntegrityViolationPropagatesUnclassified() {
        UUID shutdownId = UUID.randomUUID();
        PlannedShutdown shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId); shutdown.setStatus(PlannedShutdownStatus.APPROVED); shutdown.setWindowVersion(2L);
        shutdown.setApprovedStartAt(Instant.now()); shutdown.setApprovedEndAt(Instant.now().plusSeconds(3600));
        PlannedShutdownWorkItem item = item(shutdownId, 1);
        when(shutdowns.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        when(items.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId)).thenReturn(List.of(item));
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("fk_other_constraint");
        when(workOrderService.createGenerated(any())).thenThrow(unrelated);

        assertThatThrownBy(() -> service.generate(shutdownId,
                new PlannedShutdownWorkOrderGenerationRequest(List.of()), "unrelated"))
                .isSameAs(unrelated);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void sameHeaderAndFingerprintReplaysDurableOrderedResult() {
        UUID shutdownId = UUID.randomUUID();
        PlannedShutdown shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId); shutdown.setStatus(PlannedShutdownStatus.APPROVED); shutdown.setWindowVersion(5L);
        shutdown.setApprovedStartAt(Instant.parse("2026-07-11T10:00:00Z"));
        shutdown.setApprovedEndAt(Instant.parse("2026-07-11T12:00:00Z"));
        PlannedShutdownWorkItem item = item(shutdownId, 1);
        when(shutdowns.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        when(items.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId)).thenReturn(List.of(item));
        WorkOrderDto dto = dto(UUID.randomUUID());
        when(workOrderService.createGenerated(any())).thenReturn(dto);
        ArgumentCaptor<PlannedShutdownGenerationRequest> command = ArgumentCaptor.forClass(PlannedShutdownGenerationRequest.class);

        service.generate(shutdownId, new PlannedShutdownWorkOrderGenerationRequest(List.of()), "replay");
        verify(requests).saveAndFlush(command.capture());
        when(requests.findByPlannedShutdownIdAndIdempotencyKeyAndIsDeletedFalse(shutdownId, "replay"))
                .thenReturn(Optional.of(command.getValue()));
        WorkOrder canonical = canonical(shutdown, item, 5L); canonical.setId(dto.id());
        when(workOrders.findByIdAndIsDeletedFalse(dto.id())).thenReturn(Optional.of(canonical));
        when(workOrderService.findById(dto.id())).thenReturn(dto);

        var replay = service.generate(shutdownId, new PlannedShutdownWorkOrderGenerationRequest(List.of()), "replay");

        assertThat(replay.workOrders()).containsExactly(dto);
        verify(workOrderService, times(1)).createGenerated(any());
    }

    @Test
    void secondCreateFailureDoesNotRecordPartialCommand() {
        UUID shutdownId = UUID.randomUUID();
        PlannedShutdown shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId); shutdown.setStatus(PlannedShutdownStatus.APPROVED); shutdown.setWindowVersion(6L);
        shutdown.setApprovedStartAt(Instant.now()); shutdown.setApprovedEndAt(Instant.now().plusSeconds(3600));
        PlannedShutdownWorkItem first = item(shutdownId, 1);
        PlannedShutdownWorkItem second = item(shutdownId, 2);
        when(shutdowns.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        when(items.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of(first, second));
        when(workOrderService.createGenerated(any())).thenReturn(dto(UUID.randomUUID()))
                .thenThrow(new IllegalStateException("second create failed"));

        assertThatThrownBy(() -> service.generate(shutdownId,
                new PlannedShutdownWorkOrderGenerationRequest(List.of()), "rollback"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("second create failed");
        verify(workOrderService, times(2)).createGenerated(any());
        verify(requests, never()).saveAndFlush(any());
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

    private static WorkOrder canonical(PlannedShutdown shutdown, PlannedShutdownWorkItem item, long version) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setGenerationKey("PS:" + shutdown.getId() + ":" + item.getId() + ":" + version);
        workOrder.setPlannedShutdownId(shutdown.getId()); workOrder.setShutdownWorkItemId(item.getId());
        workOrder.setEquipmentId(item.getEquipmentId()); workOrder.setRequiresShutdown(item.isRequiresShutdown());
        workOrder.setRequiresIsolation(item.isRequiresIsolation()); workOrder.setStartPlannedAt(shutdown.getApprovedStartAt());
        workOrder.setEndPlannedAt(shutdown.getApprovedEndAt());
        return workOrder;
    }
}
