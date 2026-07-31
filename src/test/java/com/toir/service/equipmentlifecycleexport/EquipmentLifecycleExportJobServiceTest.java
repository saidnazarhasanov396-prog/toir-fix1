package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportRequests.CreateRequest;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportRequests.ScopeRequest;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportJob;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportArtifactRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportJobRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.service.AuditLogService;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipmentLifecycleExportJobServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Test
    void creationCapturesOneAsOfResolvedPolicyAndDispatchesAfterPersistence() {
        Fixture fixture = fixture();
        UUID equipmentId = UUID.randomUUID();

        var response = fixture.service.create(user(), "request-1", new CreateRequest(
                "standard-v1",
                new ScopeRequest(EquipmentLifecycleExportScopeMode.EXPLICIT_IDS, List.of(equipmentId))
        ));

        ArgumentCaptor<EquipmentLifecycleExportJob> saved = ArgumentCaptor.forClass(EquipmentLifecycleExportJob.class);
        verify(fixture.jobs).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAsOf()).isEqualTo(NOW);
        assertThat(saved.getValue().getResolvedPolicy()).isNotBlank();
        assertThat(saved.getValue().getPolicyFingerprint()).matches("[0-9a-f]{64}");
        assertThat(saved.getValue().getAuthorizationScope()).isEqualTo("{\"global\":true}");
        assertThat(response.asOf()).isEqualTo(NOW);
        verify(fixture.dispatcher).dispatchAfterCommit(saved.getValue().getId());
    }

    @Test
    void dedicatedNonAdminFreezesDepartmentAuthorizationScope() {
        Fixture fixture = fixture();
        UUID departmentId = UUID.randomUUID();
        AuthenticatedUser exporter = new AuthenticatedUser(
                UUID.randomUUID().toString(), "exporter", null, null, departmentId.toString(),
                "USER", List.of("EQUIPMENT_LIFECYCLE_DATASET_EXPORT"));

        fixture.service.create(exporter, "request-department", new CreateRequest(
                "standard-v1", new ScopeRequest(EquipmentLifecycleExportScopeMode.ALL_AUTHORIZED, List.of())));

        ArgumentCaptor<EquipmentLifecycleExportJob> saved = ArgumentCaptor.forClass(EquipmentLifecycleExportJob.class);
        verify(fixture.jobs).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAuthorizationScope())
                .isEqualTo("{\"global\":false,\"departmentId\":\"" + departmentId + "\"}");
    }

    @Test
    void sameIdempotencyKeyAndRequestReturnsExistingJobWithoutDispatch() {
        Fixture fixture = fixture();
        UUID equipmentId = UUID.randomUUID();
        CreateRequest request = new CreateRequest("standard-v1",
                new ScopeRequest(EquipmentLifecycleExportScopeMode.EXPLICIT_IDS, List.of(equipmentId)));
        EquipmentLifecycleExportJob existing = persistedJob(fixture.fingerprints
                .normalizeRequest("standard-v1", EquipmentLifecycleExportScopeMode.EXPLICIT_IDS,
                        List.of(equipmentId)).fingerprint());
        when(fixture.jobs.findByCreatorIdAndIdempotencyKey(any(), any())).thenReturn(Optional.of(existing));

        var response = fixture.service.create(user(), "request-1", request);

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(fixture.dispatcher, times(0)).dispatchAfterCommit(any());
    }

    @Test
    void sameIdempotencyKeyWithDifferentRequestConflicts() {
        Fixture fixture = fixture();
        EquipmentLifecycleExportJob existing = persistedJob("a".repeat(64));
        when(fixture.jobs.findByCreatorIdAndIdempotencyKey(any(), any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> fixture.service.create(user(), "request-1", new CreateRequest(
                "standard-v1",
                new ScopeRequest(EquipmentLifecycleExportScopeMode.ALL_AUTHORIZED, List.of())
        ))).isInstanceOf(RuntimeException.class).hasMessageContaining("different export request");
    }

    @Test
    void concurrentDuplicateCreationCannotCreateASecondJobAndReturnsSafeConflict() {
        Fixture fixture = fixture();
        when(fixture.jobs.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> fixture.service.create(user(), "request-race", new CreateRequest(
                "standard-v1",
                new ScopeRequest(EquipmentLifecycleExportScopeMode.ALL_AUTHORIZED, List.of())
        ))).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("created concurrently")
                .hasMessageNotContaining("unique violation");
        verify(fixture.dispatcher, times(0)).dispatchAfterCommit(any());
    }

    @Test
    void explicitScopeIsRequiredAndCannotBeEmpty() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service.create(user(), "request-1", new CreateRequest(
                "standard-v1",
                new ScopeRequest(EquipmentLifecycleExportScopeMode.EXPLICIT_IDS, List.of())
        ))).isInstanceOf(RuntimeException.class).hasMessageContaining("must not be empty");
    }

    @Test
    void explicitScopeCannotExceedServerMaximum() {
        Fixture fixture = fixture();
        List<UUID> ids = java.util.stream.IntStream.range(0, 10_001)
                .mapToObj(ignored -> UUID.randomUUID()).toList();

        assertThatThrownBy(() -> fixture.service.create(user(), "request-1", new CreateRequest(
                "standard-v1",
                new ScopeRequest(EquipmentLifecycleExportScopeMode.EXPLICIT_IDS, ids)
        ))).isInstanceOf(RuntimeException.class).hasMessageContaining("configured Equipment maximum");
    }

    @Test
    void completedJobCannotBeResumedOrCancelled() {
        Fixture fixture = fixture();
        EquipmentLifecycleExportJob completed = persistedJob("a".repeat(64));
        completed.setStatus(com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus.COMPLETED);
        when(fixture.jobs.findForUpdate(completed.getId())).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> fixture.service.resume(completed.getId(), user()))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("cannot be resumed");
        assertThatThrownBy(() -> fixture.service.cancel(completed.getId(), user()))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("cannot be cancelled");
    }

    @Test
    void partialArtifactsRemainUnavailableBeforeCompletedState() {
        Fixture fixture = fixture();
        EquipmentLifecycleExportJob running = persistedJob("a".repeat(64));
        running.setStatus(com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus.RUNNING);
        when(fixture.jobs.findById(running.getId())).thenReturn(Optional.of(running));

        assertThatThrownBy(() -> fixture.service.artifacts(running.getId(), user()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("only after completion");
    }

    @Test
    void nonAdminCannotReadAnotherCreatorsJob() {
        Fixture fixture = fixture();
        EquipmentLifecycleExportJob other = persistedJob("a".repeat(64));
        other.setCreatorId(UUID.randomUUID());
        when(fixture.jobs.findById(other.getId())).thenReturn(Optional.of(other));
        AuthenticatedUser departmentExporter = new AuthenticatedUser(
                UUID.randomUUID().toString(), "exporter", null, null, UUID.randomUUID().toString(),
                "USER", List.of("EQUIPMENT_LIFECYCLE_DATASET_EXPORT"));

        assertThatThrownBy(() -> fixture.service.get(other.getId(), departmentExporter))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void expiredCompletedArtifactsReturnGoneBeforeAnyStorageRead() {
        Fixture fixture = fixture();
        EquipmentLifecycleExportJob completed = persistedJob("a".repeat(64));
        completed.setStatus(com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus.COMPLETED);
        completed.setExpiresAt(NOW.minusSeconds(1));
        when(fixture.jobs.findById(completed.getId())).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> fixture.service.download(
                completed.getId(),
                com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType.DATASET,
                user()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expired");
        verify(fixture.storage, times(0)).open(any());
    }

    private Fixture fixture() {
        EquipmentLifecycleExportJobRepository jobs = mock(EquipmentLifecycleExportJobRepository.class);
        when(jobs.findByCreatorIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(jobs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        EquipmentLifecycleExportArtifactRepository artifacts = mock(EquipmentLifecycleExportArtifactRepository.class);
        when(artifacts.findAllByJobIdOrderByArtifactTypeAsc(any())).thenReturn(List.of());
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        EquipmentLifecycleExportFingerprintService fingerprints = new EquipmentLifecycleExportFingerprintService(mapper);
        EquipmentLifecycleExportDispatcher dispatcher = mock(EquipmentLifecycleExportDispatcher.class);
        EquipmentLifecycleExportStorage storage = mock(EquipmentLifecycleExportStorage.class);
        EquipmentLifecycleExportJobService service = new EquipmentLifecycleExportJobService(
                jobs, artifacts,
                new EquipmentLifecycleExportProfileResolver(properties, mapper, fingerprints),
                fingerprints, dispatcher, storage,
                mock(AuditLogService.class), properties, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, jobs, dispatcher, fingerprints, storage);
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser("11111111-1111-1111-1111-111111111111", "admin", null,
                null, null, "SYSTEM_ADMIN", List.of("*"));
    }

    private EquipmentLifecycleExportJob persistedJob(String fingerprint) {
        EquipmentLifecycleExportJob job = new EquipmentLifecycleExportJob();
        job.setId(UUID.randomUUID());
        job.setCreatorId(UUID.fromString(user().id()));
        job.setRequestFingerprint(fingerprint);
        job.setStatus(com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus.QUEUED);
        job.setAsOf(NOW);
        job.setContextProfile("standard-v1");
        job.setSelectionMode(EquipmentLifecycleExportScopeMode.EXPLICIT_IDS);
        job.setCreatedAt(NOW);
        return job;
    }

    private record Fixture(
            EquipmentLifecycleExportJobService service,
            EquipmentLifecycleExportJobRepository jobs,
            EquipmentLifecycleExportDispatcher dispatcher,
            EquipmentLifecycleExportFingerprintService fingerprints,
            EquipmentLifecycleExportStorage storage
    ) {}
}
