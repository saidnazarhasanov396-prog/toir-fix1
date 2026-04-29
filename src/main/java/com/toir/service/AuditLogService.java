package com.toir.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.HashMap;
import java.util.Map;
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
        return repository.findAllByIsDeletedFalseOrderByCreatedAtDesc(PaginationUtils.pageRequest(page, size))
                .map(this::toResponse);
    }

    private AuditLogResponseDto toResponse(AuditLog log) {
        Map<String, Object> oldMap = new HashMap<>();
        Map<String, Object> newMap = new HashMap<>();

        JsonNode diffNode = readJson(log.getDiffJson());
        if (diffNode != null && diffNode.isObject()) {
            diffNode.fieldNames().forEachRemaining(field -> {
                JsonNode change = diffNode.get(field);
                if (change != null && change.isObject()) {
                    oldMap.put(field, toJavaValue(change.get("old")));
                    newMap.put(field, toJavaValue(change.get("new")));
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
                toMap(readJson(log.getPreviousSnapshot())),
                toMap(readJson(log.getCurrentSnapshot())),
                oldMap,
                newMap,
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

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode normalized = node;
        if (!node.isObject()) {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.set("value", node);
            normalized = wrapper;
        }
        return objectMapper.convertValue(normalized, Map.class);
    }

    private Object toJavaValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, Object.class);
    }
}
