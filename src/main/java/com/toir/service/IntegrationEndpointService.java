package com.toir.service;
import com.toir.entity.IntegrationEndpoint;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.IntegrationSyncStatus;
import com.toir.repository.IntegrationEndpointRepository;

import com.toir.exception.RestException;
import com.toir.dto.integration.IntegrationEndpointDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class IntegrationEndpointService {

    private final IntegrationEndpointRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<IntegrationEndpointDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(IntegrationEndpointDto::from).toList();
    }

    public IntegrationEndpointDto create(IntegrationEndpointDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Endpoint code already exists: " + r.code());
        }
        IntegrationEndpoint e = new IntegrationEndpoint();
        apply(e, r);
        IntegrationEndpoint saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return IntegrationEndpointDto.from(saved);
    }

    public IntegrationEndpointDto update(UUID id, IntegrationEndpointDto r) {
        IntegrationEndpoint e = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        apply(e, r);
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return IntegrationEndpointDto.from(e);
    }

    public IntegrationEndpointDto recordSync(UUID id, IntegrationSyncStatus status) {
        IntegrationEndpoint e = getOrThrow(id);
        e.setLastSyncAt(Instant.now());
        e.setLastSyncStatus(status);
        return IntegrationEndpointDto.from(e);
    }

    @Transactional(readOnly = true)
    public IntegrationEndpointDto findById(UUID id) {
        return IntegrationEndpointDto.from(getOrThrow(id));
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        IntegrationEndpoint saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

    private IntegrationEndpoint getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Integration endpoint not found: " + id));
    }

    private void apply(IntegrationEndpoint e, IntegrationEndpointDto r) {
        e.setCode(r.code());
        e.setName(r.name());
        e.setSystem(r.system());
        e.setUrl(r.url());
        e.setPort(r.port());
        e.setBasePath(r.basePath());
        e.setAuthType(r.authType());
        e.setApiKey(r.apiKey());
        if (r.username() != null) e.setUsername(r.username());
        if (r.password() != null && !r.password().isBlank()) e.setPassword(r.password());
        e.setTimeoutSeconds(r.timeoutSeconds());
        e.setSyncIntervalMinutes(r.syncIntervalMinutes());
        e.setSyncWorkOrders(r.syncWorkOrders());
        e.setSyncDowntimes(r.syncDowntimes());
        e.setSyncDefects(r.syncDefects());
        e.setSyncScada(r.syncScada());
        e.setSyncProduction(r.syncProduction());
        if (r.active() != null) e.setActive(r.active());
    }

    private void audit(AuditAction action, UUID id, String oldJson, IntegrationEndpoint current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log("integration_endpoint", id != null ? id.toString() : null, action,
                AuditModule.INTEGRATION_ENDPOINT, auditMessage(action), oldJson, newJson);
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Интеграционная точка создана";
            case UPDATE -> "Интеграционная точка обновлена";
            case DELETE -> "Интеграционная точка удалена";
            default -> "Действие выполнено над интеграционной точкой";
        };
    }
}
