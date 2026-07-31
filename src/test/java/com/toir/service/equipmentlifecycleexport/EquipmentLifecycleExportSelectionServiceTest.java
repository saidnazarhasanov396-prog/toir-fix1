package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportJob;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportMembership;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportJobRepository;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportMembershipRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipmentLifecycleExportSelectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Test
    void explicitIdsAreDeduplicatedSortedMaterializedAndFrozen() {
        Fixture fixture = fixture();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        fixture.job.setRequestSnapshot("{\"equipmentIds\":[\"" + second + "\",\"" + first + "\",\"" + second + "\"]}");
        when(fixture.equipment.findActiveIdsForExport(any(), isNull())).thenReturn(List.of(first, second));

        fixture.service.freeze(fixture.job.getId(), fixture.token);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EquipmentLifecycleExportMembership>> rows =
                (ArgumentCaptor<List<EquipmentLifecycleExportMembership>>) (ArgumentCaptor<?>)
                        ArgumentCaptor.forClass(List.class);
        verify(fixture.memberships).saveAllAndFlush(rows.capture());
        assertThat(rows.getValue()).extracting(EquipmentLifecycleExportMembership::getEquipmentId)
                .containsExactly(first, second);
        assertThat(rows.getValue()).extracting(EquipmentLifecycleExportMembership::getOrdinal)
                .containsExactly(0L, 1L);
        assertThat(fixture.job.isSelectionFrozen()).isTrue();
        assertThat(fixture.job.getSelectedCount()).isEqualTo(2);
        assertThat(fixture.job.getStatus()).isEqualTo(EquipmentLifecycleExportStatus.RUNNING);
    }

    @Test
    void missingOrSoftDeletedExplicitEquipmentFailsWithoutFreezing() {
        Fixture fixture = fixture();
        UUID requested = UUID.randomUUID();
        fixture.job.setRequestSnapshot("{\"equipmentIds\":[\"" + requested + "\"]}");
        when(fixture.equipment.findActiveIdsForExport(any(), isNull())).thenReturn(List.of());

        assertThatThrownBy(() -> fixture.service.freeze(fixture.job.getId(), fixture.token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unavailable");
        assertThat(fixture.job.isSelectionFrozen()).isFalse();
    }

    @Test
    void ordinaryResumeNeverRebuildsFrozenMembership() {
        Fixture fixture = fixture();
        fixture.job.setSelectionFrozen(true);
        fixture.job.setSelectedCount(3);

        fixture.service.freeze(fixture.job.getId(), fixture.token);

        verify(fixture.memberships, never()).deleteAllByJobId(any());
        verify(fixture.equipment, never()).findActiveIdsForExport(any(), any());
    }

    @Test
    void allAuthorizedSelectionUsesUuidKeysetPagesAndFreezesOneStableOrder() {
        Fixture fixture = fixture();
        fixture.job.setSelectionMode(EquipmentLifecycleExportScopeMode.ALL_AUTHORIZED);
        fixture.properties.setSelectionBatchSize(2);
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        when(fixture.equipment.findActiveIdsForExportAfter(isNull(), isNull(), eq(2)))
                .thenReturn(List.of(first, second));
        when(fixture.equipment.findActiveIdsForExportAfter(isNull(), eq(second), eq(2))).thenReturn(List.of(third));

        fixture.service.freeze(fixture.job.getId(), fixture.token);

        assertThat(fixture.job.getSelectedCount()).isEqualTo(3);
        verify(fixture.equipment).findActiveIdsForExportAfter(isNull(), isNull(), eq(2));
        verify(fixture.equipment).findActiveIdsForExportAfter(isNull(), eq(second), eq(2));
    }

    @Test
    void emptyAllAuthorizedSelectionFailsExplicitly() {
        Fixture fixture = fixture();
        fixture.job.setSelectionMode(EquipmentLifecycleExportScopeMode.ALL_AUTHORIZED);
        when(fixture.equipment.findActiveIdsForExportAfter(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(250))).thenReturn(List.of());

        assertThatThrownBy(() -> fixture.service.freeze(fixture.job.getId(), fixture.token))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("selection is empty");
    }

    private Fixture fixture() {
        EquipmentLifecycleExportJobRepository jobs = mock(EquipmentLifecycleExportJobRepository.class);
        EquipmentLifecycleExportMembershipRepository memberships = mock(EquipmentLifecycleExportMembershipRepository.class);
        EquipmentRepository equipment = mock(EquipmentRepository.class);
        UUID token = UUID.randomUUID();
        EquipmentLifecycleExportJob job = new EquipmentLifecycleExportJob();
        job.setId(UUID.randomUUID());
        job.setStatus(EquipmentLifecycleExportStatus.PREPARING);
        job.setSelectionMode(EquipmentLifecycleExportScopeMode.EXPLICIT_IDS);
        job.setAuthorizationScope("{\"global\":true}");
        job.setLeaseToken(token);
        job.setLeaseExpiresAt(NOW.plusSeconds(60));
        when(jobs.findForUpdate(job.getId())).thenReturn(Optional.of(job));
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        EquipmentLifecycleExportSelectionService service = new EquipmentLifecycleExportSelectionService(
                jobs, memberships, equipment, properties, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, jobs, memberships, equipment, properties, job, token);
    }

    private record Fixture(
            EquipmentLifecycleExportSelectionService service,
            EquipmentLifecycleExportJobRepository jobs,
            EquipmentLifecycleExportMembershipRepository memberships,
            EquipmentRepository equipment,
            EquipmentLifecycleExportProperties properties,
            EquipmentLifecycleExportJob job,
            UUID token
    ) {}
}
