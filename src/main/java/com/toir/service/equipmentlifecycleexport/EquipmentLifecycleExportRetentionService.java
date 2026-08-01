package com.toir.service.equipmentlifecycleexport;

import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportJob;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportArtifactRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportJobRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportMembershipRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportPartRepository;
import com.toir.service.AuditLogService;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportObjectKeys;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "toir.ai.equipment-lifecycle.export", name = "enabled", havingValue = "true")
@Slf4j
public class EquipmentLifecycleExportRetentionService {
    private final EquipmentLifecycleExportJobRepository jobRepository;
    private final EquipmentLifecycleExportArtifactRepository artifactRepository;
    private final EquipmentLifecycleExportMembershipRepository membershipRepository;
    private final EquipmentLifecycleExportPartRepository partRepository;
    private final EquipmentLifecycleExportStorage storage;
    private final EquipmentLifecycleExportObjectKeys keys;
    private final EquipmentLifecycleExportProperties properties;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public EquipmentLifecycleExportRetentionService(
            EquipmentLifecycleExportJobRepository jobRepository,
            EquipmentLifecycleExportArtifactRepository artifactRepository,
            EquipmentLifecycleExportMembershipRepository membershipRepository,
            EquipmentLifecycleExportPartRepository partRepository,
            EquipmentLifecycleExportStorage storage,
            EquipmentLifecycleExportProperties properties,
            AuditLogService auditLogService,
            TransactionTemplate transactionTemplate,
            @org.springframework.beans.factory.annotation.Qualifier("equipmentLifecycleClock") Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.artifactRepository = artifactRepository;
        this.membershipRepository = membershipRepository;
        this.partRepository = partRepository;
        this.storage = storage;
        this.keys = new EquipmentLifecycleExportObjectKeys(properties.getS3().getPrefix());
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${toir.ai.equipment-lifecycle.export.retention-cleanup-delay-ms:60000}")
    public void cleanup() {
        for (CleanupClaim claim : claimBatch()) {
            try {
                cleanClaim(claim);
            } catch (Exception exception) {
                log.warn("Equipment lifecycle export cleanup will retry: jobId={}", claim.jobId());
            }
        }
    }

    List<CleanupClaim> claimBatch() {
        Instant now = clock.instant();
        List<CleanupClaim> claims = transactionTemplate.execute(status -> {
            List<EquipmentLifecycleExportJob> candidates = jobRepository.findCleanupCandidatesForUpdate(
                    now,
                    now.minus(properties.getStagingRetention()),
                    properties.getRetentionCleanupBatchSize()
            );
            return candidates.stream().map(job -> {
                UUID claimToken = UUID.randomUUID();
                boolean expiring = job.getStatus() == EquipmentLifecycleExportStatus.COMPLETED
                        && job.getExpiresAt() != null && !job.getExpiresAt().isAfter(now);
                if (expiring) {
                    job.setStatus(EquipmentLifecycleExportStatus.EXPIRED);
                    auditLogService.record(
                            job.getCreatorId(), AuditModule.EQUIPMENT_LIFECYCLE_DATASET_EXPORT,
                            "EquipmentLifecycleDatasetExport", job.getId().toString(), AuditAction.DELETE,
                            "Equipment lifecycle dataset export expired", null, null
                    );
                }
                job.setCleanupClaimToken(claimToken);
                job.setCleanupClaimUntil(now.plus(properties.getLeaseDuration()));
                job.setUpdatedAt(now);
                jobRepository.save(job);
                return new CleanupClaim(job.getId(), claimToken, job.getStatus());
            }).toList();
        });
        return claims == null ? List.of() : claims;
    }

    void cleanClaim(CleanupClaim claim) {
        boolean stagingEmpty = deletePrefix(keys.stagingPrefix(claim.jobId()));
        boolean deleteFinal = claim.status() == EquipmentLifecycleExportStatus.EXPIRED
                || claim.status() == EquipmentLifecycleExportStatus.FAILED
                || claim.status() == EquipmentLifecycleExportStatus.CANCELLED;
        boolean finalEmpty = !deleteFinal || deletePrefix(keys.finalPrefix(claim.jobId()));
        if (claim.status() == EquipmentLifecycleExportStatus.EXPIRED) {
            artifactRepository.findAllByJobIdOrderByArtifactTypeAsc(claim.jobId())
                    .forEach(artifact -> storage.delete(artifact.getObjectKey()));
            finalEmpty = finalEmpty && storage.list(
                    keys.finalPrefix(claim.jobId()), 1).isEmpty();
        }
        final boolean finalObjectsEmpty = finalEmpty;
        transactionTemplate.executeWithoutResult(status -> {
            EquipmentLifecycleExportJob job = jobRepository.findForUpdate(claim.jobId()).orElse(null);
            if (job == null || !claim.claimToken().equals(job.getCleanupClaimToken())) {
                return;
            }
            if (stagingEmpty) {
                job.setStagingCleanupComplete(true);
                if (job.getStatus() == EquipmentLifecycleExportStatus.COMPLETED) {
                    partRepository.deleteAllByJobId(job.getId());
                }
            }
            if (job.getStatus() == EquipmentLifecycleExportStatus.EXPIRED
                    && stagingEmpty && finalObjectsEmpty) {
                artifactRepository.deleteAllByJobId(job.getId());
                partRepository.deleteAllByJobId(job.getId());
                membershipRepository.deleteAllByJobId(job.getId());
                job.setCleanupComplete(true);
            } else if ((job.getStatus() == EquipmentLifecycleExportStatus.FAILED
                    || job.getStatus() == EquipmentLifecycleExportStatus.CANCELLED)
                    && stagingEmpty && finalObjectsEmpty) {
                partRepository.deleteAllByJobId(job.getId());
                membershipRepository.deleteAllByJobId(job.getId());
                job.setResumeAllowed(false);
                job.setCleanupComplete(true);
            }
            job.setCleanupClaimToken(null);
            job.setCleanupClaimUntil(null);
            job.setUpdatedAt(clock.instant());
            jobRepository.save(job);
        });
    }

    private boolean deletePrefix(String prefix) {
        List<String> batch = storage.list(prefix, properties.getStorageListBatchSize());
        batch.forEach(storage::delete);
        return storage.list(prefix, 1).isEmpty();
    }

    record CleanupClaim(UUID jobId, UUID claimToken, EquipmentLifecycleExportStatus status) {}
}
