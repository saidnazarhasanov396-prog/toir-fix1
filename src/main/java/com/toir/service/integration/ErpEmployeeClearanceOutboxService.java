package com.toir.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.integration.EmployeeClearanceReceiptRequest;
import com.toir.dto.integration.EmployeeClearanceReceiptV2Request;
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

    /**
     * Queues a reviewed clearance against the ERP offboarding case. The case revision is the
     * idempotency boundary so a later review for the same case is delivered independently.
     */
    public void queueV2(EmployeeClearanceReceiptV2Request request) {
        String key = "toir:employee-clearance:v2:" + request.offboardingCaseId() + ":" + request.sourceRevision();
        if (repository.existsByIdempotencyKey(key)) return;

        Instant now = Instant.now();
        String evidenceReference = request.evidenceReference().trim();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("employeeId", request.employeeId());
        data.put("offboardingCaseId", request.offboardingCaseId());
        data.put("moduleCode", "TOIR");
        data.put("cleared", request.cleared());
        data.put("sourceRevision", request.sourceRevision());
        data.put("evidenceReference", evidenceReference);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
        envelope.put("source", "TOIR"); envelope.put("target", "ERP"); envelope.put("type", "toir.employee-clearance.changed.v2");
        envelope.put("subject", "offboarding-case/" + request.offboardingCaseId()); envelope.put("time", now.toString()); envelope.put("kind", "EVENT");
        envelope.put("schemaVersion", "2.0"); envelope.put("aggregateType", "OFFBOARDING_CASE"); envelope.put("aggregateId", request.offboardingCaseId());
        envelope.put("aggregateVersion", request.sourceRevision()); envelope.put("idempotencyKey", key); envelope.put("data", data);

        ErpEmployeeClearanceOutboxEvent event = new ErpEmployeeClearanceOutboxEvent();
        event.setEmployeeId(request.employeeId()); event.setOffboardingCaseId(request.offboardingCaseId()); event.setModuleCode("TOIR");
        event.setSourceRevision(request.sourceRevision()); event.setCleared(request.cleared()); event.setEvidenceReference(evidenceReference);
        event.setIdempotencyKey(key);
        try { event.setPayload(objectMapper.writeValueAsString(envelope)); }
        catch (Exception exception) { throw new IllegalStateException("Cannot serialize TOIR employee clearance v2 event", exception); }
        event.setStatus("PENDING"); event.setNextAttemptAt(now); repository.save(event);
    }
}
