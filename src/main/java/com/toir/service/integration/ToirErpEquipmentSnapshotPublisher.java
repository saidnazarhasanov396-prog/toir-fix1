package com.toir.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.entity.equipment.Equipment;
import com.toir.repository.equipment.EquipmentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Queues the authoritative current equipment status picture before TOIR delta delivery is enabled. */
@Service
public class ToirErpEquipmentSnapshotPublisher {
    private static final String EVENT_TYPE = "toir.equipment.status.changed.v1";
    private final EquipmentRepository equipmentRepository;
    private final ErpEquipmentStatusOutboxService outbox;
    private final ObjectMapper objectMapper;

    public ToirErpEquipmentSnapshotPublisher(
        EquipmentRepository equipmentRepository,
        ErpEquipmentStatusOutboxService outbox,
        ObjectMapper objectMapper
    ) {
        this.equipmentRepository = equipmentRepository;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SnapshotQueueResult queueForErp(Integer requestedChunkSize) {
        int chunkSize = requestedChunkSize == null ? 250 : requestedChunkSize;
        if (chunkSize < 1 || chunkSize > 500) {
            throw new IllegalArgumentException("chunkSize must be between 1 and 500");
        }
        UUID snapshotId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        long watermark = createdAt.toEpochMilli();
        ArrayNode records = objectMapper.createArrayNode();
        for (Equipment equipment : equipmentRepository.findAllByIsDeletedFalseOrderByIdAsc()) {
            records.add(outbox.snapshotRecord(equipment));
        }
        List<ArrayNode> chunks = split(records, chunkSize);
        List<String> checksums = chunks.stream().map(chunk -> sha256(chunk.toString())).toList();
        String snapshotChecksum = snapshotChecksum(chunks, checksums);
        for (int index = 0; index < chunks.size(); index++) {
            outbox.queueSnapshotChunk(snapshotId, "TOIR_EQUIPMENT", index,
                serialize(chunkEnvelope(snapshotId, createdAt, watermark, index, chunks, checksums, snapshotChecksum)));
        }
        return new SnapshotQueueResult(snapshotId, records.size(), chunks.size(), watermark);
    }

    private ObjectNode chunkEnvelope(
        UUID snapshotId, Instant createdAt, long watermark, int index,
        List<ArrayNode> chunks, List<String> checksums, String snapshotChecksum
    ) {
        ArrayNode records = chunks.get(index);
        ObjectNode data = objectMapper.createObjectNode();
        data.put("snapshotId", snapshotId.toString());
        data.put("domain", "TOIR_EQUIPMENT");
        data.put("watermark", watermark);
        data.put("chunkIndex", index);
        data.put("chunkCount", chunks.size());
        data.put("recordCount", records.size());
        data.put("chunkChecksum", checksums.get(index));
        data.put("snapshotChecksum", snapshotChecksum);
        data.set("records", records);
        String key = "toir:equipment-snapshot:" + snapshotId + ':' + index;
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("id", UUID.nameUUIDFromBytes(("event:" + key).getBytes(StandardCharsets.UTF_8)).toString());
        envelope.put("source", "TOIR");
        envelope.put("target", "ERP");
        envelope.put("type", EVENT_TYPE);
        envelope.put("subject", "snapshot/" + snapshotId);
        envelope.put("time", createdAt.toString());
        envelope.put("kind", "SNAPSHOT_CHUNK");
        envelope.put("schemaVersion", "1.0");
        envelope.put("aggregateType", "SNAPSHOT");
        envelope.put("aggregateId", snapshotId.toString());
        envelope.put("aggregateVersion", watermark);
        envelope.put("idempotencyKey", key);
        envelope.set("data", data);
        return envelope;
    }

    private List<ArrayNode> split(ArrayNode records, int chunkSize) {
        List<ArrayNode> result = new ArrayList<>();
        if (records.isEmpty()) {
            result.add(objectMapper.createArrayNode());
            return result;
        }
        for (int start = 0; start < records.size(); start += chunkSize) {
            ArrayNode chunk = objectMapper.createArrayNode();
            for (int index = start; index < Math.min(records.size(), start + chunkSize); index++) {
                chunk.add(records.get(index));
            }
            result.add(chunk);
        }
        return result;
    }

    private String snapshotChecksum(List<ArrayNode> chunks, List<String> checksums) {
        StringBuilder manifest = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            manifest.append(index).append('|').append(chunks.get(index).size()).append('|').append(checksums.get(index)).append('\n');
        }
        return sha256(manifest.toString());
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String serialize(ObjectNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize TOIR equipment snapshot", exception);
        }
    }

    public record SnapshotQueueResult(UUID snapshotId, int recordCount, int chunkCount, long watermark) { }
}
