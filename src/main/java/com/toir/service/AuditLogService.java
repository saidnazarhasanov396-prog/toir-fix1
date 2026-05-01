package com.toir.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.dto.audit.AuditLogResponseDto;
import com.toir.enums.AuditAction;
import com.toir.entity.AuditLog;
import com.toir.repository.AuditLogRepository;

import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String module, String entityType, String entityId,
                       AuditAction action, String message, String ip, String userAgent) {
        recordDetailed(userId, module, entityType, entityId, action, message, ip, userAgent, null, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDetailed(UUID userId, String module, String entityType, String entityId,
                               AuditAction action, String message, String ip, String userAgent,
                               String diffJson, String previousSnapshot, String currentSnapshot) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setModule(module);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setMessage(message);
        entry.setIpAddress(ip);
        entry.setUserAgent(userAgent);
        entry.setDiffJson(diffJson);
        entry.setPreviousSnapshot(previousSnapshot);
        entry.setCurrentSnapshot(currentSnapshot);
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> find(int page, int size) {
        return repository.findAllByIsDeletedFalseOrderByCreatedAtDesc(PaginationUtils.updatedAtDescPageRequest(page, size))
                .map(this::toResponse);
    }

    private AuditLogResponseDto toResponse(AuditLog log) {
        ObjectNode oldNode = objectMapper.createObjectNode();
        ObjectNode newNode = objectMapper.createObjectNode();

        JsonNode diffNode = readJson(log.getDiffJson());
        if (diffNode != null && diffNode.isObject()) {
            diffNode.fieldNames().forEachRemaining(field -> {
                JsonNode change = diffNode.get(field);
                if (change != null && change.isObject()) {
                    oldNode.set(field, valueOrNull(change.get("old")));
                    newNode.set(field, valueOrNull(change.get("new")));
                }
            });
        }

        return new AuditLogResponseDto(
                log.getId(),
                log.getUserId(),
                log.getModule(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getMessage(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt(),
                toJsonObject(readJson(log.getPreviousSnapshot())),
                toJsonObject(readJson(log.getCurrentSnapshot())),
                oldNode,
                newNode,
                log.getDiffJson()
        );
    }

    private JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode toJsonObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            return node;
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("value", node);
        return wrapper;
    }

    private JsonNode valueOrNull(JsonNode node) {
        return node == null ? NullNode.getInstance() : node;
    }
}
