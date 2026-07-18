package com.toir.service.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentStatusHistory;
import com.toir.entity.integration.ErpEquipmentStatusOutboxEvent;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.integration.ErpEquipmentStatusOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Writes canonical TOIR equipment status changes before the surrounding business transaction commits. */
@Service
@RequiredArgsConstructor
public class ErpEquipmentStatusOutboxService {
    private static final String EVENT_TYPE = "toir.equipment.status.changed.v1";
    private final ErpEquipmentStatusOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public void queue(Equipment equipment, EquipmentStatusHistory history) {
        if (equipment == null || equipment.getId() == null || history == null || history.getId() == null
                || repository.existsByHistoryId(history.getId())) {
            return;
        }
        Instant occurredAt = history.getChangedAt() == null ? Instant.now() : history.getChangedAt();
        long revision = occurredAt.toEpochMilli();
        String idempotencyKey = "toir:equipment-status:" + history.getId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("equipmentId", equipment.getId());
        data.put("status", erpStatus(history.getToStatus()));
        data.put("sourceStatus", history.getToStatus().name());
        data.put("sourceRevision", revision);
        data.put("reason", requiredReason(history.getReason()));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8)));
        envelope.put("source", "TOIR");
        envelope.put("target", "ERP");
        envelope.put("type", EVENT_TYPE);
        envelope.put("subject", "equipment/" + equipment.getId());
        envelope.put("kind", "EVENT");
        envelope.put("schemaVersion", "1.0");
        envelope.put("time", occurredAt.toString());
        envelope.put("aggregateType", "EQUIPMENT");
        envelope.put("aggregateId", equipment.getId());
        envelope.put("aggregateVersion", revision);
        envelope.put("idempotencyKey", idempotencyKey);
        envelope.put("data", data);

        ErpEquipmentStatusOutboxEvent event = new ErpEquipmentStatusOutboxEvent();
        event.setEquipmentId(equipment.getId());
        event.setHistoryId(history.getId());
        event.setIdempotencyKey(idempotencyKey);
        event.setPayload(write(envelope));
        event.setStatus("PENDING");
        event.setNextAttemptAt(Instant.now());
        repository.save(event);
    }

    /** Converts the current owner state into the same typed event contract used by live status changes. */
    ObjectNode snapshotRecord(Equipment equipment) {
        if (equipment == null || equipment.getId() == null || equipment.getStatus() == null) {
            throw new IllegalArgumentException("TOIR equipment snapshot requires an ID and current status");
        }
        Instant changedAt = equipment.getUpdatedAt() == null ? Instant.EPOCH : equipment.getUpdatedAt();
        long revision = Math.max(0L, changedAt.toEpochMilli());
        ObjectNode data = objectMapper.createObjectNode();
        data.put("equipmentId", equipment.getId().toString());
        data.put("status", erpStatus(equipment.getStatus()));
        data.put("sourceStatus", equipment.getStatus().name());
        data.put("sourceRevision", revision);
        data.put("reason", "SNAPSHOT_BASELINE");
        ObjectNode record = objectMapper.createObjectNode();
        record.put("type", EVENT_TYPE);
        record.put("schemaVersion", "1.0");
        record.put("aggregateType", "EQUIPMENT");
        record.put("aggregateId", equipment.getId().toString());
        record.put("aggregateVersion", revision);
        record.set("data", data);
        return record;
    }

    /** Persists one snapshot transport chunk in the existing dispatcher outbox, idempotently by snapshot/chunk. */
    public void queueSnapshotChunk(UUID snapshotId, int chunkIndex, String payload) {
        queueSnapshotChunk(snapshotId, "TOIR_EQUIPMENT", chunkIndex, payload);
    }

    /** Reuses the reliable ERP dispatcher for another TOIR-owned snapshot domain. */
    public void queueSnapshotChunk(UUID snapshotId, String domain, int chunkIndex, String payload) {
        String normalizedDomain = domain == null || domain.isBlank() ? "TOIR_SNAPSHOT" : domain.trim().toLowerCase(java.util.Locale.ROOT);
        String key = "toir:" + normalizedDomain + ":snapshot:" + snapshotId + ':' + chunkIndex;
        UUID chunkId = UUID.nameUUIDFromBytes(key
                .getBytes(StandardCharsets.UTF_8));
        if (repository.existsByHistoryId(chunkId)) {
            return;
        }
        ErpEquipmentStatusOutboxEvent event = new ErpEquipmentStatusOutboxEvent();
        event.setEquipmentId(snapshotId);
        event.setHistoryId(chunkId);
        event.setIdempotencyKey(key);
        event.setPayload(payload);
        event.setStatus("PENDING");
        event.setNextAttemptAt(Instant.now());
        repository.save(event);
    }

    /** Persists a non-equipment TOIR owner delta in the same retried ERP delivery outbox. */
    public void queueOwnerEvent(UUID aggregateId, String idempotencyKey, String payload) {
        if (aggregateId == null || idempotencyKey == null || idempotencyKey.isBlank() || payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Aggregate ID, idempotency key and payload are required");
        }
        UUID eventId = UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        if (repository.existsByHistoryId(eventId)) return;
        ErpEquipmentStatusOutboxEvent event = new ErpEquipmentStatusOutboxEvent();
        event.setEquipmentId(aggregateId); event.setHistoryId(eventId); event.setIdempotencyKey(idempotencyKey);
        event.setPayload(payload); event.setStatus("PENDING"); event.setNextAttemptAt(Instant.now());
        repository.save(event);
    }

    String erpStatus(EquipmentStatus status) {
        return switch (status) {
            case ACTIVE -> "ACTIVE";
            case IN_REPAIR -> "IN_REPAIR";
            case STANDBY, CONSERVATION, DECOMMISSIONED, OUT_OF_SERVICE -> "INACTIVE";
        };
    }

    private String requiredReason(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TOIR equipment status history reason is required for ERP integration");
        }
        return value.trim();
    }

    private String write(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize ERP equipment status event", exception);
        }
    }
}
