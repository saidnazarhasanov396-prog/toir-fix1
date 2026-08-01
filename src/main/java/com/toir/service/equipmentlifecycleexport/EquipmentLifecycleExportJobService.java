package com.toir.service.equipmentlifecycleexport;

import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportRequests.CreateRequest;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportResponses.ArtifactDescriptor;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportResponses.JobResponse;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportArtifact;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportJob;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportArtifactRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportJobRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.service.AuditLogService;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportStorage;
import com.toir.util.PaginationUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "toir.ai.equipment-lifecycle.export", name = "enabled", havingValue = "true")
public class EquipmentLifecycleExportJobService {
    private static final String BASE_PATH = "/api/v1/ai/equipment-lifecycle/dataset-exports";
    private final EquipmentLifecycleExportJobRepository jobRepository;
    private final EquipmentLifecycleExportArtifactRepository artifactRepository;
    private final EquipmentLifecycleExportProfileResolver profileResolver;
    private final EquipmentLifecycleExportFingerprintService fingerprintService;
    private final EquipmentLifecycleExportDispatcher dispatcher;
    private final EquipmentLifecycleExportStorage storage;
    private final AuditLogService auditLogService;
    private final EquipmentLifecycleExportProperties properties;
    private final Clock clock;

    public EquipmentLifecycleExportJobService(
            EquipmentLifecycleExportJobRepository jobRepository,
            EquipmentLifecycleExportArtifactRepository artifactRepository,
            EquipmentLifecycleExportProfileResolver profileResolver,
            EquipmentLifecycleExportFingerprintService fingerprintService,
            EquipmentLifecycleExportDispatcher dispatcher,
            EquipmentLifecycleExportStorage storage,
            AuditLogService auditLogService,
            EquipmentLifecycleExportProperties properties,
            @org.springframework.beans.factory.annotation.Qualifier("equipmentLifecycleClock") Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.artifactRepository = artifactRepository;
        this.profileResolver = profileResolver;
        this.fingerprintService = fingerprintService;
        this.dispatcher = dispatcher;
        this.storage = storage;
        this.auditLogService = auditLogService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public JobResponse create(AuthenticatedUser user, String idempotencyKey, CreateRequest request) {
        UUID creatorId = requireUserId(user);
        String safeKey = requireIdempotencyKey(idempotencyKey);
        if (request == null || request.scope() == null || request.scope().mode() == null) {
            throw RestException.badRequest("Explicit dataset export scope is required");
        }
        List<UUID> requestedIds = request.scope().equipmentIds() == null
                ? List.of()
                : request.scope().equipmentIds();
        EquipmentLifecycleExportFingerprintService.NormalizedRequest normalized;
        try {
            normalized = fingerprintService.normalizeRequest(
                    request.profile(), request.scope().mode(), requestedIds);
        } catch (IllegalArgumentException exception) {
            throw RestException.badRequest(exception.getMessage());
        }
        if (request.scope().mode() == EquipmentLifecycleExportScopeMode.EXPLICIT_IDS
                && normalized.equipmentIds().isEmpty()) {
            throw RestException.badRequest("Explicit Equipment scope must not be empty");
        }
        if (normalized.equipmentIds().size() > properties.getMaximumEquipment()) {
            throw RestException.badRequest("Dataset export exceeds the configured Equipment maximum");
        }

        EquipmentLifecycleExportJob byKey = jobRepository
                .findByCreatorIdAndIdempotencyKey(creatorId, safeKey)
                .orElse(null);
        if (byKey != null) {
            if (!byKey.getRequestFingerprint().equals(normalized.fingerprint())) {
                throw RestException.conflict("Idempotency key was already used for a different export request");
            }
            return toResponse(byKey);
        }
        Instant now = clock.instant();
        EquipmentLifecycleExportProfileResolver.ResolvedProfile resolved;
        try {
            resolved = profileResolver.resolve(request.profile(), now);
        } catch (IllegalArgumentException exception) {
            throw RestException.badRequest(exception.getMessage());
        }
        EquipmentLifecycleExportJob job = new EquipmentLifecycleExportJob();
        job.setId(UUID.randomUUID());
        job.setCreatorId(creatorId);
        job.setIdempotencyKey(safeKey);
        job.setRequestFingerprint(normalized.fingerprint());
        job.setRequestSnapshot(normalized.json());
        job.setAuthorizationScope(authorizationScope(user));
        job.setStatus(EquipmentLifecycleExportStatus.QUEUED);
        job.setAsOf(now);
        job.setSchemaVersion(EquipmentLifecycleContextV1.SCHEMA_VERSION);
        job.setManifestVersion("1.0");
        job.setContextProfile(resolved.profileName());
        job.setResolvedPolicy(resolved.policyJson());
        job.setPolicyFingerprint(resolved.policyFingerprint());
        job.setSelectionMode(request.scope().mode());
        job.setLastCompletedOrdinal(-1);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        try {
            jobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException exception) {
            throw RestException.conflict(
                    "A dataset export with this idempotency key was created concurrently; retry the request");
        }
        audit(creatorId, job.getId(), AuditAction.CREATE, "Equipment lifecycle dataset export created");
        dispatcher.dispatchAfterCommit(job.getId());
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID jobId, AuthenticatedUser user) {
        return toResponse(requireAuthorizedJob(jobId, user));
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> list(
            AuthenticatedUser user,
            int page,
            int size,
            EquipmentLifecycleExportStatus status
    ) {
        var pageable = PaginationUtils.pageRequest(page, size);
        Page<EquipmentLifecycleExportJob> jobs;
        if (hasGlobalAccess(user)) {
            jobs = status == null
                    ? jobRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                    : jobRepository.findAllByStatusOrderByCreatedAtDescIdDesc(status, pageable);
        } else {
            UUID creatorId = requireUserId(user);
            jobs = status == null
                    ? jobRepository.findAllByCreatorIdOrderByCreatedAtDescIdDesc(creatorId, pageable)
                    : jobRepository.findAllByCreatorIdAndStatusOrderByCreatedAtDescIdDesc(
                            creatorId, status, pageable);
        }
        return jobs.map(this::toResponse);
    }

    @Transactional
    public JobResponse resume(UUID jobId, AuthenticatedUser user) {
        EquipmentLifecycleExportJob job = jobRepository.findForUpdate(jobId)
                .orElseThrow(() -> RestException.notFound("Dataset export job not found"));
        requireAuthorized(job, user);
        Instant now = clock.instant();
        boolean expiredLease = job.getLeaseExpiresAt() != null && !job.getLeaseExpiresAt().isAfter(now);
        if (job.getStatus() == EquipmentLifecycleExportStatus.COMPLETED
                || job.getStatus() == EquipmentLifecycleExportStatus.CANCELLED
                || job.getStatus() == EquipmentLifecycleExportStatus.EXPIRED) {
            throw RestException.conflict("Dataset export cannot be resumed in the current state");
        }
        if (job.getStatus() == EquipmentLifecycleExportStatus.FAILED && !job.isResumeAllowed()) {
            throw RestException.conflict("Dataset export failure is not resumable");
        }
        if (job.getStatus().active() && !expiredLease && job.getStatus() != EquipmentLifecycleExportStatus.QUEUED) {
            return toResponse(job);
        }
        job.setLeaseOwner(null);
        job.setLeaseToken(null);
        job.setLeaseExpiresAt(null);
        job.setHeartbeatAt(null);
        job.setUpdatedAt(now);
        jobRepository.save(job);
        UUID actorId = requireUserId(user);
        audit(actorId, jobId, AuditAction.UPDATE, "Equipment lifecycle dataset export resume requested");
        dispatcher.dispatchAfterCommit(jobId);
        return toResponse(job);
    }

    @Transactional
    public JobResponse cancel(UUID jobId, AuthenticatedUser user) {
        EquipmentLifecycleExportJob job = jobRepository.findForUpdate(jobId)
                .orElseThrow(() -> RestException.notFound("Dataset export job not found"));
        requireAuthorized(job, user);
        Instant now = clock.instant();
        if (job.getStatus() == EquipmentLifecycleExportStatus.CANCELLED
                || job.getStatus() == EquipmentLifecycleExportStatus.CANCEL_REQUESTED) {
            return toResponse(job);
        }
        if (job.getStatus() == EquipmentLifecycleExportStatus.COMPLETED
                || job.getStatus() == EquipmentLifecycleExportStatus.EXPIRED) {
            throw RestException.conflict("Completed or expired dataset export cannot be cancelled");
        }
        if (job.getStatus() == EquipmentLifecycleExportStatus.QUEUED
                || job.getStatus() == EquipmentLifecycleExportStatus.FAILED) {
            job.setStatus(EquipmentLifecycleExportStatus.CANCELLED);
            job.setCancelledAt(now);
            job.setResumeAllowed(false);
        } else {
            job.setStatus(EquipmentLifecycleExportStatus.CANCEL_REQUESTED);
            job.setCancelRequestedAt(now);
        }
        job.setUpdatedAt(now);
        jobRepository.save(job);
        audit(requireUserId(user), jobId, AuditAction.CANCEL, "Equipment lifecycle dataset export cancellation requested");
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<ArtifactDescriptor> artifacts(UUID jobId, AuthenticatedUser user) {
        EquipmentLifecycleExportJob job = requireDownloadable(jobId, user);
        return artifactRepository.findAllByJobIdOrderByArtifactTypeAsc(job.getId()).stream()
                .map(this::descriptor)
                .toList();
    }

    public ArtifactDownload download(
            UUID jobId,
            EquipmentLifecycleExportArtifactType type,
            AuthenticatedUser user
    ) {
        EquipmentLifecycleExportJob job = requireDownloadable(jobId, user);
        EquipmentLifecycleExportArtifact artifact = artifactRepository.findByJobIdAndArtifactType(jobId, type)
                .orElseThrow(() -> RestException.conflict("Dataset export artifact is unavailable"));
        EquipmentLifecycleExportStorage.StoredObject object = storage.open(artifact.getObjectKey());
        if (object.metadata().size() != artifact.getObjectSize()
                || !object.metadata().sha256().equals(artifact.getSha256())) {
            try {
                object.close();
            } catch (Exception ignored) {
            }
            throw RestException.conflict("Dataset export artifact integrity verification failed");
        }
        audit(requireUserId(user), jobId, AuditAction.EXPORT,
                "Equipment lifecycle dataset export artifact downloaded: " + type.name());
        return new ArtifactDownload(type, object);
    }

    private EquipmentLifecycleExportJob requireDownloadable(UUID jobId, AuthenticatedUser user) {
        EquipmentLifecycleExportJob job = requireAuthorizedJob(jobId, user);
        Instant now = clock.instant();
        if (job.getStatus() == EquipmentLifecycleExportStatus.EXPIRED
                || (job.getExpiresAt() != null && !job.getExpiresAt().isAfter(now))) {
            throw new RestException("Dataset export artifacts have expired", HttpStatus.GONE);
        }
        if (job.getStatus() != EquipmentLifecycleExportStatus.COMPLETED) {
            throw RestException.conflict("Dataset export artifacts are available only after completion");
        }
        return job;
    }

    private EquipmentLifecycleExportJob requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> RestException.notFound("Dataset export job not found"));
    }

    private EquipmentLifecycleExportJob requireAuthorizedJob(UUID jobId, AuthenticatedUser user) {
        EquipmentLifecycleExportJob job = requireJob(jobId);
        requireAuthorized(job, user);
        return job;
    }

    private static void requireAuthorized(EquipmentLifecycleExportJob job, AuthenticatedUser user) {
        if (!hasGlobalAccess(user) && !job.getCreatorId().equals(requireUserId(user))) {
            throw RestException.notFound("Dataset export job not found");
        }
    }

    private JobResponse toResponse(EquipmentLifecycleExportJob job) {
        List<ArtifactDescriptor> descriptors = job.getStatus() == EquipmentLifecycleExportStatus.COMPLETED
                && job.getExpiresAt() != null && job.getExpiresAt().isAfter(clock.instant())
                ? artifactRepository.findAllByJobIdOrderByArtifactTypeAsc(job.getId()).stream()
                    .map(this::descriptor).toList()
                : List.of();
        return new JobResponse(
                job.getId(), job.getStatus(), job.getAsOf(), job.getContextProfile(), job.getSelectionMode(),
                job.isSelectionFrozen(), job.getSelectedCount(), job.getCompletedCount(), job.getCreatedAt(),
                job.getStartedAt(), job.getCompletedAt(), job.getExpiresAt(), job.getFailureCode(),
                job.getFailureSummary(), job.getFailureEquipmentId(), job.isResumeAllowed(),
                BASE_PATH + "/" + job.getId(), descriptors
        );
    }

    private ArtifactDescriptor descriptor(EquipmentLifecycleExportArtifact artifact) {
        return new ArtifactDescriptor(
                artifact.getArtifactType(), artifact.getFilename(), artifact.getMediaType(),
                artifact.getObjectSize(), artifact.getSha256()
        );
    }

    private static UUID requireUserId(AuthenticatedUser user) {
        try {
            return UUID.fromString(user.id());
        } catch (Exception exception) {
            throw RestException.forbidden("Authenticated user identity is unavailable");
        }
    }

    private static String requireIdempotencyKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty() || key.length() > 160 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw RestException.badRequest("A valid Idempotency-Key is required");
        }
        return key;
    }

    private static String authorizationScope(AuthenticatedUser user) {
        boolean global = hasGlobalAccess(user);
        if (global) {
            return "{\"global\":true}";
        }
        try {
            UUID departmentId = UUID.fromString(user.departmentId());
            return "{\"global\":false,\"departmentId\":\"" + departmentId + "\"}";
        } catch (Exception exception) {
            throw RestException.forbidden("A department authorization scope is required for dataset export");
        }
    }

    private static boolean hasGlobalAccess(AuthenticatedUser user) {
        return user != null && ("SYSTEM_ADMIN".equals(user.primaryRoleCode())
                || (user.permissions() != null && user.permissions().contains("*")));
    }

    private void audit(UUID actorId, UUID jobId, AuditAction action, String message) {
        auditLogService.record(actorId, AuditModule.EQUIPMENT_LIFECYCLE_DATASET_EXPORT,
                "EquipmentLifecycleDatasetExport", jobId.toString(), action, message, null, null);
    }

    public record ArtifactDownload(
            EquipmentLifecycleExportArtifactType type,
            EquipmentLifecycleExportStorage.StoredObject object
    ) {}
}
