package com.toir.service.sparepartlifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.entity.sparepartlifecycle.SparePartLifecycleCommand;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandType;
import com.toir.exception.RestException;
import com.toir.repository.sparepartlifecycle.SparePartLifecycleCommandRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SparePartLifecycleCommandCoordinator {

    private final SparePartLifecycleCommandRepository repository;
    private final ObjectMapper objectMapper;

    public LifecycleCommandHandle acquire(String idempotencyKey,
                                          SparePartLifecycleCommandType commandType,
                                          Object request,
                                          UUID actorId,
                                          UUID workOrderId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw RestException.badRequest("IDEMPOTENCY_KEY_REQUIRED: Idempotency-Key header is required");
        }
        if (commandType == null || request == null || actorId == null) {
            throw RestException.badRequest("LIFECYCLE_COMMAND_INVALID: type, request and actor are required");
        }
        String normalizedKey = idempotencyKey.trim();
        String hash = requestHash(request);
        SparePartLifecycleCommand existing = repository.findByIdempotencyKeyForUpdate(normalizedKey).orElse(null);
        if (existing != null) {
            if (existing.getCommandType() != commandType || !Objects.equals(existing.getRequestHash(), hash)) {
                throw RestException.conflict("IDEMPOTENCY_KEY_REUSED: key was already used for a different command payload");
            }
            if (existing.getStatus() == SparePartLifecycleCommandStatus.IN_PROGRESS) {
                throw RestException.conflict("IDEMPOTENCY_IN_PROGRESS: the same lifecycle command is still in progress; retry later");
            }
            return new LifecycleCommandHandle(existing, true);
        }

        SparePartLifecycleCommand command = new SparePartLifecycleCommand();
        command.setIdempotencyKey(normalizedKey);
        command.setCommandType(commandType);
        command.setRequestHash(hash);
        command.setStatus(SparePartLifecycleCommandStatus.IN_PROGRESS);
        command.setWorkOrderId(workOrderId);
        command.setCreatedBy(actorId);
        return new LifecycleCommandHandle(repository.save(command), false);
    }

    public SparePartLifecycleCommand complete(SparePartLifecycleCommand command,
                                              UUID resultInstallationId,
                                              UUID resultRemovedInstallationId) {
        command.setResultInstallationId(resultInstallationId);
        command.setResultRemovedInstallationId(resultRemovedInstallationId);
        command.setStatus(SparePartLifecycleCommandStatus.SUCCEEDED);
        command.setCompletedAt(Instant.now());
        return repository.save(command);
    }

    public String requestHash(Object request) {
        try {
            JsonNode canonicalTree = sortJson(objectMapper.valueToTree(request));
            byte[] canonical = objectMapper.writeValueAsBytes(canonicalTree);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash lifecycle command", exception);
        }
    }

    private JsonNode sortJson(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> names = new java.util.ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> sorted.set(name, sortJson(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(child -> sorted.add(sortJson(child)));
            return sorted;
        }
        return node;
    }

    public record LifecycleCommandHandle(SparePartLifecycleCommand command, boolean replay) {
    }
}
