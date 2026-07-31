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

    @SuppressWarnings("unchecked")
    private TransactionTemplate immediateTransactions() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        when(template.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return template;
    }
}
