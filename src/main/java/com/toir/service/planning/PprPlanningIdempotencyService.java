package com.toir.service.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.planning.PprPlanningOperationReceipt;
import com.toir.exception.RestException;
import com.toir.repository.planning.PprPlanningOperationReceiptRepository;
import com.toir.repository.planning.PprPlanningSessionRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PprPlanningIdempotencyService {

    private final PprPlanningSessionRepository sessions;
    private final PprPlanningOperationReceiptRepository receipts;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> T execute(
            UUID sessionId,
            String operationType,
            String idempotencyKey,
            Object request,
            Class<T> responseType,
            Supplier<T> operation) {
        String normalizedKey = normalizeKey(idempotencyKey);
        String requestHash = hash(request);

        sessions.findByIdAndIsDeletedFalseForUpdate(sessionId)
                .orElseThrow(() -> RestException.notFound(
                        "PPR planning session not found: " + sessionId));

        var existing = receipts
                .findBySessionIdAndOperationTypeAndIdempotencyKeyAndIsDeletedFalse(
                        sessionId, operationType, normalizedKey);
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getRequestHash(), requestHash)) {
                throw RestException.conflict(
                        "Idempotency-Key was already used with a different payload",
                        "PPR_IDEMPOTENCY_KEY_REUSED");
            }
            return read(existing.get().getResponseJson(), responseType);
        }

        T response = operation.get();
        PprPlanningOperationReceipt receipt = new PprPlanningOperationReceipt();
        receipt.setSessionId(sessionId);
        receipt.setOperationType(operationType);
        receipt.setIdempotencyKey(normalizedKey);
        receipt.setRequestHash(requestHash);
        receipt.setResponseJson(write(response));
        receipts.saveAndFlush(receipt);
        return response;
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw RestException.badRequest(
                    "IDEMPOTENCY_KEY_REQUIRED: Idempotency-Key is required");
        }
        String normalized = key.trim();
        if (normalized.length() > 160) {
            throw RestException.badRequest(
                    "IDEMPOTENCY_KEY_INVALID: Idempotency-Key is too long");
        }
        return normalized;
    }

    private String hash(Object request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to hash PPR planning request", exception);
        }
    }

    private String write(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to store idempotent PPR response", exception);
        }
    }

    private <T> T read(String responseJson, Class<T> responseType) {
        try {
            return objectMapper.readValue(responseJson, responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to restore idempotent PPR response", exception);
        }
    }
}
