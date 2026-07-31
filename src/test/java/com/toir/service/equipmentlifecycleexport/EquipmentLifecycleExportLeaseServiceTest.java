package com.toir.service.equipmentlifecycleexport;

import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportJob;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportArtifactRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportJobRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportPartRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquipmentLifecycleExportLeaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Test
    void expiredLeaseCanBeClaimedWithANewFencingToken() {
        EquipmentLifecycleExportJobRepository jobs = mock(EquipmentLifecycleExportJobRepository.class);
        EquipmentLifecycleExportJob job = queuedJob();
        job.setStatus(EquipmentLifecycleExportStatus.RUNNING);
        job.setSelectionFrozen(true);
        job.setLeaseToken(UUID.randomUUID());
        job.setLeaseExpiresAt(NOW.minusSeconds(1));
        when(jobs.findForUpdate(job.getId())).thenReturn(Optional.of(job));
        EquipmentLifecycleExportLeaseService service = service(jobs);

        var claim = service.claim(job.getId(), "worker-b");

        assertThat(claim.fencingToken()).isNotNull();
        assertThat(claim.fencingToken()).isEqualTo(job.getLeaseToken());
        assertThat(job.getLeaseExpiresAt()).isAfter(NOW);
    }

    @Test
    void activeLeasePreventsASecondWorker() {
        EquipmentLifecycleExportJobRepository jobs = mock(EquipmentLifecycleExportJobRepository.class);
        EquipmentLifecycleExportJob job = queuedJob();
        job.setStatus(EquipmentLifecycleExportStatus.RUNNING);
        job.setSelectionFrozen(true);
        job.setLeaseToken(UUID.randomUUID());
        job.setLeaseExpiresAt(NOW.plusSeconds(30));
        when(jobs.findForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service(jobs).claim(job.getId(), "worker-b"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already claimed");
    }

    @Test
    void staleFencingTokenCannotPublishPartCheckpoint() {
        EquipmentLifecycleExportJobRepository jobs = mock(EquipmentLifecycleExportJobRepository.class);
        EquipmentLifecycleExportJob job = queuedJob();
        job.setStatus(EquipmentLifecycleExportStatus.RUNNING);
        job.setSelectionFrozen(true);
        job.setSelectedCount(1);
        job.setLeaseToken(UUID.randomUUID());
        job.setLeaseExpiresAt(NOW.plusSeconds(30));
        when(jobs.findForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service(jobs).checkpoint(job.getId(), UUID.randomUUID(),
                new EquipmentLifecycleExportLeaseService.PartCheckpoint(
                        0, 0, 0, 1, "exports/staging/part", 10, "a".repeat(64))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("lease");
        assertThat(job.getCompletedCount()).isZero();
    }

    private EquipmentLifecycleExportLeaseService service(EquipmentLifecycleExportJobRepository jobs) {
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        properties.getS3().setPrefix("exports/");
        return new EquipmentLifecycleExportLeaseService(
                jobs,
                mock(EquipmentLifecycleExportPartRepository.class),
                mock(EquipmentLifecycleExportArtifactRepository.class),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private EquipmentLifecycleExportJob queuedJob() {
        EquipmentLifecycleExportJob job = new EquipmentLifecycleExportJob();
        job.setId(UUID.randomUUID());
        job.setStatus(EquipmentLifecycleExportStatus.QUEUED);
        job.setCreatedAt(NOW.minusSeconds(10));
        job.setUpdatedAt(NOW.minusSeconds(10));
        job.setLastCompletedOrdinal(-1);
        return job;
    }
}
