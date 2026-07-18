package com.toir.service.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.ExternalEntityLink;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentStatusHistory;
import com.toir.entity.integration.AtilEquipmentStatusOutboxEvent;
import com.toir.repository.ExternalEntityLinkRepository;
import com.toir.repository.integration.AtilEquipmentStatusOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Queues status events only for equipment with an authoritative ATIL vehicle mapping. */
@Service
@RequiredArgsConstructor
public class AtilEquipmentStatusOutboxService {
    private static final String EVENT_TYPE = "toir.equipment.status.changed.v1";
    private final AtilEquipmentStatusOutboxRepository repository;
    private final ExternalEntityLinkRepository links;
    private final ObjectMapper objectMapper;

    public void queue(Equipment equipment, EquipmentStatusHistory history) {
        if (equipment == null || equipment.getId() == null || history == null || history.getId() == null
                || repository.existsByHistoryId(history.getId())) return;
        ExternalEntityLink link = links
                .findBySourceSystemAndSourceEntityTypeAndTargetSystemAndTargetEntityTypeAndTargetEntityIdAndIsDeletedFalse(
                        "ATIL", "VEHICLE", "TOIR", "EQUIPMENT", equipment.getId())
                .orElse(null);
        if (link == null) return;
        UUID vehicleId;
        try { vehicleId = UUID.fromString(link.getSourceEntityId()); }
        catch (IllegalArgumentException exception) { return; }

        Instant occurredAt = history.getChangedAt() == null ? Instant.now() : history.getChangedAt();
        long revision = occurredAt.toEpochMilli();
        String key = "toir:equipment-status-atil:" + history.getId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vehicleId", vehicleId); data.put("equipmentId", equipment.getId());
        data.put("sourceStatus", history.getToStatus().name()); data.put("sourceRevision", revision);
        data.put("reason", history.getReason());
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
        envelope.put("source", "TOIR"); envelope.put("target", "ATIL"); envelope.put("type", EVENT_TYPE);
        envelope.put("subject", "equipment/" + equipment.getId()); envelope.put("time", occurredAt.toString());
        envelope.put("kind", "EVENT"); envelope.put("schemaVersion", "1.0"); envelope.put("aggregateType", "EQUIPMENT");
        envelope.put("aggregateId", equipment.getId()); envelope.put("aggregateVersion", revision); envelope.put("idempotencyKey", key);
        envelope.put("data", data);
        AtilEquipmentStatusOutboxEvent event = new AtilEquipmentStatusOutboxEvent();
        event.setEquipmentId(equipment.getId()); event.setAtilVehicleId(vehicleId); event.setHistoryId(history.getId());
        event.setIdempotencyKey(key); event.setPayload(write(envelope)); event.setStatus("PENDING"); event.setNextAttemptAt(Instant.now());
        repository.save(event);
    }

    private String write(Map<String, Object> envelope) {
        try { return objectMapper.writeValueAsString(envelope); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize ATIL equipment status event", exception); }
    }
}
