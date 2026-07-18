package com.toir.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentStatusHistory;
import com.toir.entity.integration.ErpEquipmentStatusOutboxEvent;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.integration.ErpEquipmentStatusOutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ErpEquipmentStatusOutboxServiceTest {
    @Test
    void queuesCanonicalErpStatusEnvelopeWithMappedStatus() {
        ErpEquipmentStatusOutboxRepository repository = mock(ErpEquipmentStatusOutboxRepository.class);
        when(repository.existsByHistoryId(any())).thenReturn(false);
        ErpEquipmentStatusOutboxService service = new ErpEquipmentStatusOutboxService(repository, new ObjectMapper());
        UUID equipmentId = UUID.randomUUID(); UUID historyId = UUID.randomUUID();
        Equipment equipment = new Equipment(); equipment.setId(equipmentId);
        EquipmentStatusHistory history = new EquipmentStatusHistory(); history.setId(historyId);
        history.setToStatus(EquipmentStatus.OUT_OF_SERVICE); history.setReason("Safety lockout");
        history.setChangedAt(Instant.parse("2026-07-17T12:00:00Z"));

        service.queue(equipment, history);

        ArgumentCaptor<ErpEquipmentStatusOutboxEvent> event = ArgumentCaptor.forClass(ErpEquipmentStatusOutboxEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(event.getValue().getPayload()).contains(
                "\"source\":\"TOIR\"", "\"target\":\"ERP\"", "\"type\":\"toir.equipment.status.changed.v1\"",
                "\"equipmentId\":\"" + equipmentId + "\"", "\"status\":\"INACTIVE\"", "\"sourceStatus\":\"OUT_OF_SERVICE\"");
    }

    @Test
    void refusesToQueueAStatusEventWithoutTheContractRequiredReason() {
        ErpEquipmentStatusOutboxRepository repository = mock(ErpEquipmentStatusOutboxRepository.class);
        when(repository.existsByHistoryId(any())).thenReturn(false);
        ErpEquipmentStatusOutboxService service = new ErpEquipmentStatusOutboxService(repository, new ObjectMapper());
        Equipment equipment = new Equipment(); equipment.setId(UUID.randomUUID());
        EquipmentStatusHistory history = new EquipmentStatusHistory(); history.setId(UUID.randomUUID());
        history.setToStatus(EquipmentStatus.OUT_OF_SERVICE); history.setChangedAt(Instant.parse("2026-07-17T12:00:00Z"));

        assertThatThrownBy(() -> service.queue(equipment, history))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void buildsCurrentEquipmentSnapshotRecordUsingTheLiveStatusContract() {
        ErpEquipmentStatusOutboxRepository repository = mock(ErpEquipmentStatusOutboxRepository.class);
        ErpEquipmentStatusOutboxService service = new ErpEquipmentStatusOutboxService(repository, new ObjectMapper());
        Equipment equipment = new Equipment(); equipment.setId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.IN_REPAIR); equipment.setUpdatedAt(Instant.parse("2026-07-17T12:00:00Z"));

        var record = service.snapshotRecord(equipment);

        assertThat(record.path("type").asText()).isEqualTo("toir.equipment.status.changed.v1");
        assertThat(record.path("aggregateType").asText()).isEqualTo("EQUIPMENT");
        assertThat(record.path("data").path("equipmentId").asText()).isEqualTo(equipment.getId().toString());
        assertThat(record.path("data").path("status").asText()).isEqualTo("IN_REPAIR");
        assertThat(record.path("data").path("reason").asText()).isEqualTo("SNAPSHOT_BASELINE");
    }
}
