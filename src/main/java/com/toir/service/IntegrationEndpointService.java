package com.toir.service;
import com.toir.entity.IntegrationEndpoint;
import com.toir.enums.IntegrationSyncStatus;
import com.toir.repository.IntegrationEndpointRepository;

import com.toir.exception.RestException;
import com.toir.dto.integration.IntegrationEndpointDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegrationEndpointService {

    private final IntegrationEndpointRepository repository;


    @Transactional(readOnly = true)
    public List<IntegrationEndpointDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(IntegrationEndpointDto::from).toList();
    }

    public IntegrationEndpointDto create(IntegrationEndpointDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Endpoint code already exists: " + r.code());
        }
        IntegrationEndpoint e = new IntegrationEndpoint();
        apply(e, r);
        return IntegrationEndpointDto.from(repository.save(e));
    }

    public IntegrationEndpointDto update(UUID id, IntegrationEndpointDto r) {
        IntegrationEndpoint e = getOrThrow(id);
        apply(e, r);
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
        entity.setDeleted(true);
        repository.save(entity); }

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
}
