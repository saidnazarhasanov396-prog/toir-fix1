package com.toir.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.dto.audit.AuditLogResponseDto;
import com.toir.dto.user.UserDto;
import com.toir.enums.AuditAction;
import com.toir.entity.AuditLog;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.AuditLogRepository;

import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, AuditModule module, String entityType, String entityId,
                       AuditAction action, String message, String ip, String userAgent) {
        recordDetailed(userId, module, entityType, entityId, action, message, ip, userAgent, null, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDetailed(UUID userId, AuditModule module, String entityType, String entityId,
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
        entry.setCreatedAt(Instant.now().atZone(ZoneId.of("Asia/Tashkent")).toInstant());
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> find(int page, int size, AuditAction action, LocalDate fromDate, LocalDate toDate, String search,UUID userId) {
        String actionStr = action != null ? action.name() : null;
        String searchPattern = search != null ? "%" + search + "%" : null;
        return repository.findAllByIsDeletedFalseOrderByCreatedAtDesc(actionStr, fromDate, toDate, searchPattern, userId, PaginationUtils.pageRequest(page, size))
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
        UserDto user = resolveUser(log.getUserId());

        return new AuditLogResponseDto(
                log.getId(),
                user,
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

    private UserDto resolveUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userService.findById(userId);
        } catch (RestException ignored) {
            return null;
        }
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
