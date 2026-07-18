package com.toir.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.repository.WorkOrderRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Queues the TOIR-authoritative work-order view without changing ERP's independently owned maintenance workflow. */
@Service
public class ToirErpWorkOrderSnapshotPublisher {
    private static final String DOMAIN = "TOIR_WORK_ORDER";
    private static final String EVENT_TYPE = "toir.work-order.changed.v1";
    private final WorkOrderRepository workOrders;
    private final ErpEquipmentStatusOutboxService outbox;
    private final ObjectMapper objectMapper;

    public ToirErpWorkOrderSnapshotPublisher(WorkOrderRepository workOrders, ErpEquipmentStatusOutboxService outbox,
                                             ObjectMapper objectMapper) {
        this.workOrders = workOrders; this.outbox = outbox; this.objectMapper = objectMapper;
    }

    @Transactional
    public SnapshotQueueResult queueForErp(Integer requestedChunkSize) {
        int chunkSize = requestedChunkSize == null ? 250 : requestedChunkSize;
        if (chunkSize < 1 || chunkSize > 500) throw new IllegalArgumentException("chunkSize must be between 1 and 500");
        UUID snapshotId = UUID.randomUUID(); Instant createdAt = Instant.now(); long watermark = createdAt.toEpochMilli();
        ArrayNode records = objectMapper.createArrayNode();
        for (WorkOrder workOrder : workOrders.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) records.add(record(workOrder));
        List<ArrayNode> chunks = split(records, chunkSize);
        List<String> checksums = chunks.stream().map(chunk -> sha256(chunk.toString())).toList();
        String snapshotChecksum = snapshotChecksum(chunks, checksums);
        for (int index = 0; index < chunks.size(); index++) {
            outbox.queueSnapshotChunk(snapshotId, DOMAIN, index,
                    serialize(chunkEnvelope(snapshotId, createdAt, watermark, index, chunks, checksums, snapshotChecksum)));
        }
        return new SnapshotQueueResult(snapshotId, records.size(), chunks.size(), watermark);
    }

    /** Queues a later owner revision for delta replay after the work-order baseline has been staged. */
    @Transactional
    public void queueDelta(UUID workOrderId) {
        WorkOrder workOrder = workOrders.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> new IllegalArgumentException("TOIR work order was not found: " + workOrderId));
        ObjectNode record = record(workOrder);
        long revision = record.path("aggregateVersion").asLong();
        String key = "toir:work-order:" + workOrderId + ':' + revision;
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("id", UUID.nameUUIDFromBytes(("event:" + key).getBytes(StandardCharsets.UTF_8)).toString());
        envelope.put("source", "TOIR"); envelope.put("target", "ERP"); envelope.put("type", EVENT_TYPE);
        envelope.put("subject", "work-orders/" + workOrderId); envelope.put("time", workOrder.getUpdatedAt().toString());
        envelope.put("kind", "EVENT"); envelope.put("schemaVersion", "1.0"); envelope.put("aggregateType", "WORK_ORDER");
        envelope.put("aggregateId", workOrderId.toString()); envelope.put("aggregateVersion", revision); envelope.put("idempotencyKey", key);
        envelope.set("data", record.path("data"));
        outbox.queueOwnerEvent(workOrderId, key, serialize(envelope));
    }

    private ObjectNode record(WorkOrder workOrder) {
        if (workOrder.getId() == null || workOrder.getUpdatedAt() == null || workOrder.getStatus() == null) {
            throw new IllegalStateException("TOIR work-order snapshot requires id, updatedAt and status");
        }
        long revision = Math.max(0L, workOrder.getUpdatedAt().toEpochMilli());
        ObjectNode data = objectMapper.createObjectNode();
        data.put("workOrderId", workOrder.getId().toString()); data.put("number", workOrder.getNumber()); data.put("title", workOrder.getTitle());
        data.put("equipmentExternalId", workOrder.getEquipmentId().toString()); data.put("departmentExternalId", workOrder.getDepartmentId().toString());
        data.put("status", workOrder.getStatus().name()); data.put("type", workOrder.getType().name()); data.put("workType", workOrder.getWorkType().name());
        data.put("priority", workOrder.getPriority().name()); data.put("plannedStartAt", time(workOrder.getStartPlannedAt()));
        data.put("plannedEndAt", time(workOrder.getEndPlannedAt())); data.put("startedAt", time(workOrder.getStartedAt()));
        data.put("completedAt", time(workOrder.getCompletedAt()));
        data.put("active", workOrder.getStatus() != com.toir.enums.WorkOrderStatus.CLOSED
                && workOrder.getStatus() != com.toir.enums.WorkOrderStatus.CANCELLED);
        data.put("sourceRevision", revision);
        ObjectNode record = objectMapper.createObjectNode();
        record.put("type", EVENT_TYPE); record.put("schemaVersion", "1.0"); record.put("aggregateType", "WORK_ORDER");
        record.put("aggregateId", workOrder.getId().toString()); record.put("aggregateVersion", revision); record.set("data", data);
        return record;
    }

    private ObjectNode chunkEnvelope(UUID snapshotId, Instant createdAt, long watermark, int index, List<ArrayNode> chunks,
                                     List<String> checksums, String snapshotChecksum) {
        ArrayNode records = chunks.get(index); ObjectNode data = objectMapper.createObjectNode();
        data.put("snapshotId", snapshotId.toString()); data.put("domain", DOMAIN); data.put("watermark", watermark);
        data.put("chunkIndex", index); data.put("chunkCount", chunks.size()); data.put("recordCount", records.size());
        data.put("chunkChecksum", checksums.get(index)); data.put("snapshotChecksum", snapshotChecksum); data.set("records", records);
        String key = "toir:work-order-snapshot:" + snapshotId + ':' + index;
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("id", UUID.nameUUIDFromBytes(("event:" + key).getBytes(StandardCharsets.UTF_8)).toString());
        envelope.put("source", "TOIR"); envelope.put("target", "ERP"); envelope.put("type", EVENT_TYPE);
        envelope.put("subject", "snapshot/" + snapshotId); envelope.put("time", createdAt.toString()); envelope.put("kind", "SNAPSHOT_CHUNK");
        envelope.put("schemaVersion", "1.0"); envelope.put("aggregateType", "SNAPSHOT"); envelope.put("aggregateId", snapshotId.toString());
        envelope.put("aggregateVersion", watermark); envelope.put("idempotencyKey", key); envelope.set("data", data);
        return envelope;
    }

    private List<ArrayNode> split(ArrayNode records, int chunkSize) {
        List<ArrayNode> result = new ArrayList<>();
        if (records.isEmpty()) { result.add(objectMapper.createArrayNode()); return result; }
        for (int start = 0; start < records.size(); start += chunkSize) {
            ArrayNode chunk = objectMapper.createArrayNode();
            for (int index = start; index < Math.min(records.size(), start + chunkSize); index++) chunk.add(records.get(index));
            result.add(chunk);
        }
        return result;
    }

    private String snapshotChecksum(List<ArrayNode> chunks, List<String> checksums) {
        StringBuilder manifest = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) manifest.append(index).append('|').append(chunks.get(index).size()).append('|').append(checksums.get(index)).append('\n');
        return sha256(manifest.toString());
    }
    private String sha256(String value) { try { byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder result=new StringBuilder(); for(byte item:digest) result.append(String.format("%02x",item)); return result.toString(); } catch(Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); } }
    private String serialize(ObjectNode envelope) { try { return objectMapper.writeValueAsString(envelope); } catch(Exception exception) { throw new IllegalStateException("Cannot serialize TOIR work-order snapshot", exception); } }
    private String time(Instant value) { return value == null ? null : value.toString(); }
    public record SnapshotQueueResult(UUID snapshotId, int recordCount, int chunkCount, long watermark) { }
}
