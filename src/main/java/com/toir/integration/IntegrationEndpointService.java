package com.toir.integration;

import com.toir.common.exception.RestException;
import com.toir.common.web.PaginatedResponse;
import com.toir.integration.dto.IntegrationEndpointDto;
import com.toir.integration.dto.IntegrationSyncLogDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class IntegrationEndpointService {

    private final IntegrationEndpointRepository repository;

    public IntegrationEndpointService(IntegrationEndpointRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<IntegrationEndpointDto> findAll() {
        return repository.findAll().stream().map(IntegrationEndpointDto::from).toList();
    }

    public IntegrationEndpointDto create(IntegrationEndpointDto r) {
        if (repository.existsByCode(r.code())) {
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

    @Transactional(readOnly = true)
    public PaginatedResponse<IntegrationSyncLogDto> findSyncLogs(UUID endpointId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.clamp(pageSize, 1, 200);

        List<IntegrationEndpointDto> endpoints = endpointId != null
                ? List.of(findById(endpointId))
                : findAll();

        List<IntegrationSyncLogDto> all = endpoints.stream()
                .filter(e -> e.lastSyncAt() != null || e.lastSyncStatus() != null)
                .map(e -> new IntegrationSyncLogDto(
                        e.id(),
                        e.code(),
                        e.name(),
                        e.system(),
                        e.url(),
                        e.lastSyncAt(),
                        e.lastSyncStatus(),
                        e.lastSyncStatus() != null ? "Last recorded sync status" : "No sync status recorded"
                ))
                .toList();

        int fromIndex = Math.min((safePage - 1) * safePageSize, all.size());
        int toIndex = Math.min(fromIndex + safePageSize, all.size());
        return new PaginatedResponse<>(
                all.subList(fromIndex, toIndex),
                new PaginatedResponse.Meta(safePage, safePageSize, all.size())
        );
    }

    public void delete(UUID id) { repository.delete(getOrThrow(id)); }

    private IntegrationEndpoint getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Integration endpoint not found: " + id));
    }

    private void apply(IntegrationEndpoint e, IntegrationEndpointDto r) {
        e.setCode(r.code());
        e.setName(r.name());
        e.setSystem(r.system());
        e.setUrl(r.url());
        e.setAuthType(r.authType());
        if (r.active() != null) e.setActive(r.active());
    }
}
