package com.toir.service.equipmentlifecycleexport;

import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportArtifact;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportJob;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportPart;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportArtifactRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportJobRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportPartRepository;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportObjectKeys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "toir.ai.equipment-lifecycle.export", name = "enabled", havingValue = "true")
public class EquipmentLifecycleExportLeaseService {
    private final EquipmentLifecycleExportJobRepository jobRepository;
    private final EquipmentLifecycleExportPartRepository partRepository;
    private final EquipmentLifecycleExportArtifactRepository artifactRepository;
    private final EquipmentLifecycleExportProperties properties;
    private final EquipmentLifecycleExportObjectKeys keys;
    private final Clock clock;

    public EquipmentLifecycleExportLeaseService(
            EquipmentLifecycleExportJobRepository jobRepository,
            EquipmentLifecycleExportPartRepository partRepository,
            EquipmentLifecycleExportArtifactRepository artifactRepository,
            EquipmentLifecycleExportProperties properties,
            @org.springframework.beans.factory.annotation.Qualifier("equipmentLifecycleClock") Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.partRepository = partRepository;
        this.artifactRepository = artifactRepository;
        this.properties = properties;
        this.keys = new EquipmentLifecycleExportObjectKeys(properties.getS3().getPrefix());
        this.clock = clock;
    }

    @Transactional
    public LeaseClaim claim(UUID jobId, String workerId) {
        Instant now = clock.instant();
        EquipmentLifecycleExportJob job = locked(jobId);
        if (job.getStatus() == EquipmentLifecycleExportStatus.CANCEL_REQUESTED) {
            cancelLocked(job, now);
            throw RestException.conflict("Dataset export was cancelled");
        }
        if (job.getStatus() == EquipmentLifecycleExportStatus.COMPLETED
                || job.getStatus() == EquipmentLifecycleExportStatus.CANCELLED
                || job.getStatus() == EquipmentLifecycleExportStatus.EXPIRED) {
            throw RestException.conflict("Dataset export cannot be claimed in the current state");
        }
        boolean leaseActive = job.getLeaseToken() != null && job.getLeaseExpiresAt() != null
                && job.getLeaseExpiresAt().isAfter(now);
        if (leaseActive) {
            throw RestException.conflict("Dataset export is already claimed by an active worker");
        }
        UUID token = UUID.randomUUID();
        job.setLeaseOwner(sanitizeWorker(workerId));
        job.setLeaseToken(token);
        job.setLeaseExpiresAt(now.plus(properties.getLeaseDuration()));
        job.setHeartbeatAt(now);
        job.setFailureCode(null);
        job.setFailureSummary(null);
        job.setFailureEquipmentId(null);
        job.setResumeAllowed(false);
        job.setStatus(job.isSelectionFrozen()
                ? EquipmentLifecycleExportStatus.RUNNING
                : EquipmentLifecycleExportStatus.PREPARING);
        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        job.setUpdatedAt(now);
        jobRepository.save(job);
        return new LeaseClaim(job.getId(), token, job.getStatus(), job.isSelectionFrozen());
    }

    @Transactional
    public boolean continueProcessing(UUID jobId, UUID token) {
        Instant now = clock.instant();
        EquipmentLifecycleExportJob job = locked(jobId);
        if (job.getStatus() == EquipmentLifecycleExportStatus.CANCEL_REQUESTED) {
            requireToken(job, token);
            cancelLocked(job, now);
            return false;
        }
        requireLease(job, token, now);
        job.setHeartbeatAt(now);
        job.setLeaseExpiresAt(now.plus(properties.getLeaseDuration()));
        job.setUpdatedAt(now);
        jobRepository.save(job);
        return true;
    }

    @Transactional(readOnly = true)
    public OwnedJob ownedJob(UUID jobId, UUID token) {
        EquipmentLifecycleExportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> RestException.notFound("Dataset export job not found"));
        requireLease(job, token, clock.instant());
        return new OwnedJob(
                job.getId(), job.getAsOf(), job.getResolvedPolicy(), job.getPolicyFingerprint(),
                job.getRequestFingerprint(), job.getContextProfile(), job.getSelectionMode(),
                job.isSelectionFrozen(), job.getSelectedCount(), job.getCompletedCount(),
                job.getLastCompletedOrdinal(), job.getFinalizationTime(), job.getCreatedAt(), job.getStartedAt()
        );
    }

    @Transactional
    public void checkpoint(UUID jobId, UUID token, PartCheckpoint checkpoint) {
        Instant now = clock.instant();
        EquipmentLifecycleExportJob job = locked(jobId);
        requireLease(job, token, now);
        if (job.getStatus() != EquipmentLifecycleExportStatus.RUNNING) {
            throw RestException.conflict("Dataset export checkpoint is not allowed in the current state");
        }
        long expectedFirst = job.getLastCompletedOrdinal() + 1;
        if (checkpoint.firstOrdinal() != expectedFirst
                || checkpoint.lastOrdinal() != checkpoint.firstOrdinal() + checkpoint.recordCount() - 1
                || job.getCompletedCount() + checkpoint.recordCount() > job.getSelectedCount()) {
            throw RestException.conflict("Dataset export checkpoint ordinal sequence is invalid");
        }
        String expectedObjectKey = keys.part(
                jobId, token, checkpoint.partNumber(), checkpoint.firstOrdinal(), checkpoint.lastOrdinal());
        if (!expectedObjectKey.equals(checkpoint.objectKey())) {
            throw RestException.conflict("Dataset export checkpoint object identity is invalid");
        }
        if (partRepository.findByJobIdAndPartNumber(jobId, checkpoint.partNumber()).isPresent()) {
            throw RestException.conflict("Dataset export part number is already committed");
        }
        EquipmentLifecycleExportPart part = new EquipmentLifecycleExportPart();
        part.setId(UUID.randomUUID());
        part.setJobId(jobId);
        part.setPartNumber(checkpoint.partNumber());
        part.setFirstOrdinal(checkpoint.firstOrdinal());
        part.setLastOrdinal(checkpoint.lastOrdinal());
        part.setRecordCount(checkpoint.recordCount());
        part.setObjectKey(checkpoint.objectKey());
        part.setObjectSize(checkpoint.objectSize());
        part.setSha256(checkpoint.sha256());
        part.setFencingToken(token);
        part.setCreatedAt(now);
        partRepository.save(part);
        job.setLastCompletedOrdinal(checkpoint.lastOrdinal());
        job.setCompletedCount(job.getCompletedCount() + checkpoint.recordCount());
        job.setHeartbeatAt(now);
        job.setLeaseExpiresAt(now.plus(properties.getLeaseDuration()));
        job.setUpdatedAt(now);
        jobRepository.save(job);
    }

    @Transactional
    public Instant beginFinalizing(UUID jobId, UUID token) {
        Instant now = clock.instant();
        EquipmentLifecycleExportJob job = locked(jobId);
        requireLease(job, token, now);
        if (job.getStatus() != EquipmentLifecycleExportStatus.RUNNING
                || !job.isSelectionFrozen()
                || job.getCompletedCount() != job.getSelectedCount()
                || job.getLastCompletedOrdinal() != job.getSelectedCount() - 1) {
            throw RestException.conflict("Dataset export cannot be finalized before all frozen ordinals are committed");
        }
        job.setStatus(EquipmentLifecycleExportStatus.FINALIZING);
        if (job.getFinalizationTime() == null) {
            job.setFinalizationTime(now);
        }
        job.setUpdatedAt(now);
        jobRepository.save(job);
        return job.getFinalizationTime();
    }

    @Transactional
    public void complete(UUID jobId, UUID token, List<FinalArtifact> finalArtifacts) {
        Instant now = clock.instant();
        EquipmentLifecycleExportJob job = locked(jobId);
        requireLease(job, token, now);
        if (job.getStatus() != EquipmentLifecycleExportStatus.FINALIZING
                || finalArtifacts == null
                || finalArtifacts.size() != EquipmentLifecycleExportArtifactType.values().length) {
            throw RestException.conflict("Dataset export final artifact set is incomplete");
        }
        artifactRepository.deleteAllByJobId(jobId);
        List<EquipmentLifecycleExportArtifact> rows = finalArtifacts.stream().map(item -> {
            EquipmentLifecycleExportArtifact row = new EquipmentLifecycleExportArtifact();
            row.setId(UUID.randomUUID());
            row.setJobId(jobId);
            row.setArtifactType(item.type());
            row.setFilename(item.type().getFilename());
            row.setMediaType(item.type().getMediaType());
            row.setObjectKey(item.objectKey());
            row.setObjectSize(item.objectSize());
            row.setSha256(item.sha256());
            row.setCreatedAt(now);
            return row;
        }).toList();
        if (rows.stream().map(EquipmentLifecycleExportArtifact::getArtifactType).distinct().count()
                != EquipmentLifecycleExportArtifactType.values().length) {
            throw RestException.conflict("Dataset export final artifact types are incomplete");
        }
        boolean invalidObjectIdentity = rows.stream().anyMatch(row -> !keys.finalArtifact(
                jobId, token, row.getArtifactType()).equals(row.getObjectKey()));
        if (invalidObjectIdentity) {
            throw RestException.conflict("Dataset export final artifact object identity is invalid");
        }
        artifactRepository.saveAllAndFlush(rows);
        Instant completedAt = job.getFinalizationTime() == null ? now : job.getFinalizationTime();
        job.setStatus(EquipmentLifecycleExportStatus.COMPLETED);
        job.setCompletedAt(completedAt);
        job.setExpiresAt(completedAt.plus(properties.getCompletedRetention()));
        clearLease(job);
        job.setResumeAllowed(false);
        job.setUpdatedAt(now);
        jobRepository.save(job);
    }

    @Transactional
    public void fail(
            UUID jobId,
            UUID token,
            String code,
            String safeSummary,
            UUID equipmentId,
            boolean resumeAllowed
    ) {
        EquipmentLifecycleExportJob job = locked(jobId);
        if (job.getLeaseToken() == null || token == null || !token.equals(job.getLeaseToken())) {
            return;
        }
        Instant now = clock.instant();
        if (job.getLeaseExpiresAt() == null || !job.getLeaseExpiresAt().isAfter(now)) {
            return;
        }
        if (job.getStatus() == EquipmentLifecycleExportStatus.CANCEL_REQUESTED) {
            cancelLocked(job, now);
            return;
        }
        if (!job.getStatus().terminal()) {
            job.setStatus(EquipmentLifecycleExportStatus.FAILED);
            job.setFailureCode(sanitize(code, 80));
            job.setFailureSummary(sanitize(safeSummary, 500));
            job.setFailureEquipmentId(equipmentId);
            job.setResumeAllowed(resumeAllowed);
            clearLease(job);
            job.setUpdatedAt(now);
            jobRepository.save(job);
        }
    }

    private EquipmentLifecycleExportJob locked(UUID jobId) {
        return jobRepository.findForUpdate(jobId)
                .orElseThrow(() -> RestException.notFound("Dataset export job not found"));
    }

    private static void requireLease(EquipmentLifecycleExportJob job, UUID token, Instant now) {
        requireToken(job, token);
        if (job.getLeaseExpiresAt() == null || !job.getLeaseExpiresAt().isAfter(now)) {
            throw RestException.conflict("Dataset export worker lease is no longer valid");
        }
    }

    private static void requireToken(EquipmentLifecycleExportJob job, UUID token) {
        if (token == null || !token.equals(job.getLeaseToken())) {
            throw RestException.conflict("Dataset export worker lease token is no longer valid");
        }
    }

    private void cancelLocked(EquipmentLifecycleExportJob job, Instant now) {
        job.setStatus(EquipmentLifecycleExportStatus.CANCELLED);
        job.setCancelledAt(now);
        job.setResumeAllowed(false);
        clearLease(job);
        job.setUpdatedAt(now);
        jobRepository.save(job);
    }

    private static void clearLease(EquipmentLifecycleExportJob job) {
        job.setLeaseOwner(null);
        job.setLeaseToken(null);
        job.setLeaseExpiresAt(null);
        job.setHeartbeatAt(null);
    }

    private static String sanitizeWorker(String workerId) {
        return sanitize(workerId == null ? "worker" : workerId, 160);
    }

    private static String sanitize(String value, int limit) {
        String safe = value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ").trim();
        return safe.length() <= limit ? safe : safe.substring(0, limit);
    }

    public record LeaseClaim(
            UUID jobId,
            UUID fencingToken,
            EquipmentLifecycleExportStatus status,
            boolean selectionFrozen
    ) {}

    public record OwnedJob(
            UUID jobId,
            Instant asOf,
            String resolvedPolicyJson,
            String policyFingerprint,
            String requestFingerprint,
            String contextProfile,
            com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode selectionMode,
            boolean selectionFrozen,
            long selectedCount,
            long completedCount,
            long lastCompletedOrdinal,
            Instant finalizationTime,
            Instant createdAt,
            Instant startedAt
    ) {}

    public record PartCheckpoint(
            int partNumber,
            long firstOrdinal,
            long lastOrdinal,
            int recordCount,
            String objectKey,
            long objectSize,
            String sha256
    ) {}

    public record FinalArtifact(
            EquipmentLifecycleExportArtifactType type,
            String objectKey,
            long objectSize,
            String sha256
    ) {}
}
