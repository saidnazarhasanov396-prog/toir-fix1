package com.toir.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.repository.WorkOrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ToirErpWorkOrderSnapshotPublisherTest {
    @Test
    void queuesChunkedOwnerWorkOrderSnapshotThroughTransactionalOutbox() throws Exception {
        WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
        ErpEquipmentStatusOutboxService outbox = mock(ErpEquipmentStatusOutboxService.class);
        WorkOrder order = WorkOrder.builder().number("WO-42").title("Pump repair").equipmentId(UUID.randomUUID())
                .departmentId(UUID.randomUUID()).status(WorkOrderStatus.IN_PROGRESS).type(WorkOrderType.PLANNED)
                .workType(WorkType.REPAIR).priority(PriorityLevel.HIGH).build();
        order.setId(UUID.randomUUID()); order.setUpdatedAt(Instant.parse("2026-07-17T12:00:00Z"));
        when(workOrders.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(order));
        ToirErpWorkOrderSnapshotPublisher publisher = new ToirErpWorkOrderSnapshotPublisher(workOrders, outbox, new ObjectMapper());

        var queued = publisher.queueForErp(1);

        assertThat(queued.recordCount()).isEqualTo(1); assertThat(queued.chunkCount()).isEqualTo(1);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox, times(1)).queueSnapshotChunk(eq(queued.snapshotId()), eq("TOIR_WORK_ORDER"), eq(0), payload.capture());
        var envelope = new ObjectMapper().readTree(payload.getValue());
        assertThat(envelope.path("kind").asText()).isEqualTo("SNAPSHOT_CHUNK");
        assertThat(envelope.path("data").path("domain").asText()).isEqualTo("TOIR_WORK_ORDER");
        assertThat(envelope.path("data").path("records").get(0).path("type").asText()).isEqualTo("toir.work-order.changed.v1");
        assertThat(envelope.path("data").path("records").get(0).path("data").path("status").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void queuesCurrentWorkOrderRevisionForDeltaReplay() throws Exception {
        WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
        ErpEquipmentStatusOutboxService outbox = mock(ErpEquipmentStatusOutboxService.class);
        WorkOrder order = WorkOrder.builder().number("WO-43").title("Valve repair").equipmentId(UUID.randomUUID())
                .departmentId(UUID.randomUUID()).status(WorkOrderStatus.CLOSED).type(WorkOrderType.DEFECT)
                .workType(WorkType.REPAIR).priority(PriorityLevel.CRITICAL).build();
        order.setId(UUID.randomUUID()); order.setUpdatedAt(Instant.parse("2026-07-17T12:10:00Z"));
        when(workOrders.findByIdAndIsDeletedFalse(order.getId())).thenReturn(java.util.Optional.of(order));
        ToirErpWorkOrderSnapshotPublisher publisher = new ToirErpWorkOrderSnapshotPublisher(workOrders, outbox, new ObjectMapper());

        publisher.queueDelta(order.getId());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).queueOwnerEvent(eq(order.getId()), key.capture(), payload.capture());
        assertThat(key.getValue()).contains("toir:work-order:" + order.getId());
        var envelope = new ObjectMapper().readTree(payload.getValue());
        assertThat(envelope.path("kind").asText()).isEqualTo("EVENT");
        assertThat(envelope.path("data").path("status").asText()).isEqualTo("CLOSED");
        assertThat(envelope.path("data").path("active").asBoolean()).isFalse();
    }
}
