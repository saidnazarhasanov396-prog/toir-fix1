package com.toir.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.integration.EmployeeClearanceReceiptRequest;
import com.toir.entity.integration.ErpEmployeeClearanceOutboxEvent;
import com.toir.repository.integration.ErpEmployeeClearanceOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Stores a reviewed TOIR offboarding receipt before it is delivered to ERP. */
@Service
@RequiredArgsConstructor
public class ErpEmployeeClearanceOutboxService {
    private final ErpEmployeeClearanceOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public void queue(EmployeeClearanceReceiptRequest request) {
        String key = "toir:employee-clearance:" + request.employeeId() + ":" + request.sourceRevision();
        if (repository.existsByIdempotencyKey(key)) return;
        Instant now = Instant.now();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("employeeId", request.employeeId()); data.put("moduleCode", "TOIR");
        data.put("cleared", request.cleared()); data.put("sourceRevision", request.sourceRevision());
        if (request.evidenceReference() != null && !request.evidenceReference().isBlank()) data.put("evidenceReference", request.evidenceReference().trim());
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
        envelope.put("source", "TOIR"); envelope.put("target", "ERP"); envelope.put("type", "toir.employee-clearance.changed.v1");
        envelope.put("subject", "employee/" + request.employeeId()); envelope.put("time", now.toString()); envelope.put("kind", "EVENT");
        envelope.put("schemaVersion", "1.0"); envelope.put("aggregateType", "EMPLOYEE"); envelope.put("aggregateId", request.employeeId());
        envelope.put("aggregateVersion", request.sourceRevision()); envelope.put("idempotencyKey", key); envelope.put("data", data);
        ErpEmployeeClearanceOutboxEvent event = new ErpEmployeeClearanceOutboxEvent();
        event.setEmployeeId(request.employeeId()); event.setModuleCode("TOIR"); event.setSourceRevision(request.sourceRevision());
        event.setCleared(request.cleared()); event.setEvidenceReference(request.evidenceReference()); event.setIdempotencyKey(key);
        try { event.setPayload(objectMapper.writeValueAsString(envelope)); }
        catch (Exception exception) { throw new IllegalStateException("Cannot serialize TOIR employee clearance event", exception); }
        event.setStatus("PENDING"); event.setNextAttemptAt(now); repository.save(event);
    }
}
