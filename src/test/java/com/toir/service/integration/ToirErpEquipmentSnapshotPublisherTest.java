package com.toir.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.equipment.EquipmentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ToirErpEquipmentSnapshotPublisherTest {

    @Test
    void queuesStableEquipmentSnapshotThroughTheExistingErpOutbox() throws Exception {
        EquipmentRepository equipmentRepository = Mockito.mock(EquipmentRepository.class);
        ErpEquipmentStatusOutboxService outbox = Mockito.mock(ErpEquipmentStatusOutboxService.class);
        ObjectMapper mapper = new ObjectMapper();
        Equipment equipment = new Equipment(); equipment.setId(UUID.randomUUID()); equipment.setStatus(EquipmentStatus.ACTIVE);
        when(equipmentRepository.findAllByIsDeletedFalseOrderByIdAsc()).thenReturn(List.of(equipment));
        JsonNode record = mapper.readTree("""
            {"type":"toir.equipment.status.changed.v1","schemaVersion":"1.0","aggregateType":"EQUIPMENT",
             "aggregateId":"%s","aggregateVersion":42,
             "data":{"equipmentId":"%s","status":"ACTIVE","sourceStatus":"ACTIVE","sourceRevision":42,"reason":"SNAPSHOT_BASELINE"}}
            """.formatted(equipment.getId(), equipment.getId()));
        when(outbox.snapshotRecord(equipment)).thenReturn((com.fasterxml.jackson.databind.node.ObjectNode) record);
        ToirErpEquipmentSnapshotPublisher publisher = new ToirErpEquipmentSnapshotPublisher(equipmentRepository, outbox, mapper);

        var result = publisher.queueForErp(10);

        assertThat(result.recordCount()).isEqualTo(1);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outbox).queueSnapshotChunk(org.mockito.ArgumentMatchers.eq(result.snapshotId()), org.mockito.ArgumentMatchers.eq("TOIR_EQUIPMENT"), org.mockito.ArgumentMatchers.eq(0), payloadCaptor.capture());
        JsonNode envelope = mapper.readTree(payloadCaptor.getValue());
        assertThat(envelope.path("kind").asText()).isEqualTo("SNAPSHOT_CHUNK");
        assertThat(envelope.path("data").path("domain").asText()).isEqualTo("TOIR_EQUIPMENT");
        assertThat(envelope.path("data").path("records").get(0).path("aggregateId").asText()).isEqualTo(equipment.getId().toString());
        assertThat(sha256(envelope.path("data").path("records").toString()))
            .isEqualTo(envelope.path("data").path("chunkChecksum").asText());
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
