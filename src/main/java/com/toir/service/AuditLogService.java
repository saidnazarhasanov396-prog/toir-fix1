package com.toir.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.audit.AuditLogWriteCommand;
import com.toir.audit.AuditLogWriteScheduler;
import com.toir.dto.audit.AuditLogResponseDto;
import com.toir.dto.audit.AuditLogUserSummary;
import com.toir.dto.user.UserDto;
import com.toir.enums.AuditAction;
import com.toir.entity.AuditLog;
import com.toir.enums.AuditModule;
import com.toir.repository.AuditLogRepository;


import com.toir.repository.users.UserRepository;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final AuditLogWriteScheduler writeScheduler;

    public void record(UUID userId, AuditModule module, String entityType, String entityId,
                       AuditAction action, String message, String ip, String userAgent) {
        recordDetailed(userId, module, entityType, entityId, action, message, ip, userAgent, null, null, null);
    }

    public void recordDetailed(UUID userId, AuditModule module, String entityType, String entityId,
                               AuditAction action, String message, String ip, String userAgent,
                               String diffJson, String previousSnapshot, String currentSnapshot) {
        recordDetailed(userId, module, entityType, entityId, action, message, ip, userAgent,
                diffJson, previousSnapshot, currentSnapshot, null, null, null, null, null);
    }

    public void recordDetailed(UUID userId, AuditModule module, String entityType, String entityId,
                               AuditAction action, String message, String ip, String userAgent,
                               String diffJson, String previousSnapshot, String currentSnapshot,
                               String reason, String source, String requestMethod, String requestPath, String correlationId) {
        writeScheduler.schedule(new AuditLogWriteCommand(
                userId,
                module,
                entityType,
                entityId,
                action,
                message,
                ip,
                userAgent,
                diffJson,
                previousSnapshot,
                currentSnapshot,
                reason,
                source,
                requestMethod,
                requestPath,
                correlationId
        ));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> find(int page, int size, AuditAction action, LocalDate fromDate, LocalDate toDate, String search,UUID userId) {
        return find(page, size, null, action, fromDate, toDate, search, userId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> find(int page, int size, AuditModule module, AuditAction action, LocalDate fromDate, LocalDate toDate, String search, UUID userId) {
        String moduleStr = module != null ? module.name() : null;
        String actionStr = action != null ? action.name() : null;
        String searchPattern = search != null ? "%" + search + "%" : null;
        Page<AuditLog> logs = repository.findAllByIsDeletedFalseOrderByCreatedAtDesc(
                moduleStr,
                actionStr,
                fromDate,
                toDate,
                searchPattern,
                userId,
                PaginationUtils.pageRequest(page, size)
        );
        Map<UUID, AuditLogUserSummary> usersById = loadUsers(logs);
        return logs.map(log -> toResponse(log, usersById));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> find(int page, int size, AuditAction action, LocalDate fromDate, LocalDate toDate,
                                          String search, UUID userId, Sort sort) {
        return find(page, size, null, action, fromDate, toDate, search, userId, sort);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> find(int page, int size, AuditModule module, AuditAction action, LocalDate fromDate, LocalDate toDate,
                                          String search, UUID userId, Sort sort) {
        Page<AuditLog> logs = repository.findAll(
                auditLogSpecification(module, action, fromDate, toDate, search, userId),
                PaginationUtils.pageRequest(page, size, sort == null ? Sort.by(Sort.Direction.DESC, "createdAt") : sort)
        );
        Map<UUID, AuditLogUserSummary> usersById = loadUsers(logs);
        return logs.map(log -> toResponse(log, usersById));
    }

    private Specification<AuditLog> auditLogSpecification(AuditModule module, AuditAction action, LocalDate fromDate, LocalDate toDate,
                                                         String search, UUID userId) {
        return (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (module != null) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate.atStartOfDay(ZoneId.of("Asia/Tashkent")).toInstant()));
            }
            if (toDate != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), toDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Tashkent")).toInstant()));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("message"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("entityType"), "")), pattern)
                ));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private AuditLogResponseDto toResponse(AuditLog log, Map<UUID, AuditLogUserSummary> usersById) {
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
        UserDto user = resolveUser(log.getUserId(), usersById);

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
                log.getReason(),
                log.getSource(),
                log.getRequestMethod(),
                log.getRequestPath(),
                log.getCorrelationId(),
                log.getCreatedAt(),
                toJsonObject(readJson(log.getPreviousSnapshot())),
                toJsonObject(readJson(log.getCurrentSnapshot())),
                oldNode,
                newNode,
                log.getDiffJson()
        );
    }

    private Map<UUID, AuditLogUserSummary> loadUsers(Page<AuditLog> logs) {
        Set<UUID> userIds = logs.getContent().stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAuditLogUserSummariesByIdIn(userIds).stream()
                .collect(Collectors.toMap(AuditLogUserSummary::id, Function.identity()));
    }

    private UserDto resolveUser(UUID userId, Map<UUID, AuditLogUserSummary> usersById) {
        if (userId == null) {
            return null;
        }
        AuditLogUserSummary user = usersById.get(userId);
        return user == null ? null : user.toUserDto();
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
