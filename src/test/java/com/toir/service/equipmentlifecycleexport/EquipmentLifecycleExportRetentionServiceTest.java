package com.toir.service.equipmentlifecycleexport;

import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportJob;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportArtifactRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportJobRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportMembershipRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportPartRepository;
import com.toir.service.AuditLogService;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportStorage;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipmentLifecycleExportRetentionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Test
    void expiredCompletedJobTransitionsToExpiredInsideClaimBeforeObjectIo() {
        EquipmentLifecycleExportJobRepository jobs = mock(EquipmentLifecycleExportJobRepository.class);
        EquipmentLifecycleExportJob job = new EquipmentLifecycleExportJob();
        job.setId(UUID.randomUUID());
        job.setCreatorId(UUID.randomUUID());
        job.setStatus(EquipmentLifecycleExportStatus.COMPLETED);
        job.setExpiresAt(NOW.minusSeconds(1));
        job.setUpdatedAt(NOW.minusSeconds(10));
        when(jobs.findCleanupCandidatesForUpdate(eq(NOW), any(), eq(20))).thenReturn(List.of(job));
        TransactionTemplate transactions = immediateTransactions();
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        properties.getS3().setPrefix("exports/");
        EquipmentLifecycleExportStorage storage = mock(EquipmentLifecycleExportStorage.class);
        EquipmentLifecycleExportRetentionService service = new EquipmentLifecycleExportRetentionService(
                jobs, mock(EquipmentLifecycleExportArtifactRepository.class),
                mock(EquipmentLifecycleExportMembershipRepository.class),
                mock(EquipmentLifecycleExportPartRepository.class), storage, properties,
                mock(AuditLogService.class), transactions, Clock.fixed(NOW, ZoneOffset.UTC));

        List<EquipmentLifecycleExportRetentionService.CleanupClaim> claims = service.claimBatch();

        assertThat(job.getStatus()).isEqualTo(EquipmentLifecycleExportStatus.EXPIRED);
        assertThat(claims).hasSize(1);
        assertThat(job.getCleanupClaimToken()).isEqualTo(claims.get(0).claimToken());
        verify(jobs).findCleanupCandidatesForUpdate(eq(NOW), any(), eq(20));
    }

    @Test
    void expiredCleanupDeletesOnlyBoundedManagedPrefixesAndMinimalMetadataIdempotently() {
        EquipmentLifecycleExportJobRepository jobs = mock(EquipmentLifecycleExportJobRepository.class);
        EquipmentLifecycleExportArtifactRepository artifacts = mock(EquipmentLifecycleExportArtifactRepository.class);
        EquipmentLifecycleExportMembershipRepository memberships =
                mock(EquipmentLifecycleExportMembershipRepository.class);
        EquipmentLifecycleExportPartRepository parts = mock(EquipmentLifecycleExportPartRepository.class);
        EquipmentLifecycleExportStorage storage = mock(EquipmentLifecycleExportStorage.class);
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        properties.getS3().setPrefix("exports/");
        UUID jobId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        EquipmentLifecycleExportJob job = new EquipmentLifecycleExportJob();
        job.setId(jobId);
        job.setStatus(EquipmentLifecycleExportStatus.EXPIRED);
        job.setCleanupClaimToken(claimToken);
        when(jobs.findForUpdate(jobId)).thenReturn(java.util.Optional.of(job));
        String stagingPrefix = "exports/staging/" + jobId + "/";
        String finalPrefix = "exports/final/" + jobId + "/";
        when(storage.list(stagingPrefix, 200))
                .thenReturn(List.of(stagingPrefix + "orphan"), List.of());
        when(storage.list(finalPrefix, 200))
                .thenReturn(List.of(finalPrefix + "artifact"), List.of());
        when(storage.list(stagingPrefix, 1)).thenReturn(List.of());
        when(storage.list(finalPrefix, 1)).thenReturn(List.of());
        TransactionTemplate transactions = immediateTransactions();
        EquipmentLifecycleExportRetentionService service = new EquipmentLifecycleExportRetentionService(
                jobs, artifacts, memberships, parts, storage, properties,
                mock(AuditLogService.class), transactions, Clock.fixed(NOW, ZoneOffset.UTC));

        service.cleanClaim(new EquipmentLifecycleExportRetentionService.CleanupClaim(
                jobId, claimToken, EquipmentLifecycleExportStatus.EXPIRED));

        verify(storage).delete(stagingPrefix + "orphan");
        verify(storage).delete(finalPrefix + "artifact");
        verify(artifacts).deleteAllByJobId(jobId);
        verify(parts).deleteAllByJobId(jobId);
        verify(memberships).deleteAllByJobId(jobId);
        assertThat(job.isCleanupComplete()).isTrue();
        assertThat(job.getCleanupClaimToken()).isNull();
    }

    @SuppressWarnings("unchecked")
    private TransactionTemplate immediateTransactions() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        when(template.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(template).executeWithoutResult(any());
        return template;
    }
}
