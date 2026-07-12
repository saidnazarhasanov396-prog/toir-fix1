package com.toir.service.repair;

import com.toir.dto.repaircampaign.*;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.plannedshutdown.PlannedShutdownCampaignLink;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignWorkItem;
import com.toir.entity.repair.RepairCampaignWorkItemWindow;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownCampaignLinkRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignWorkItemRepository;
import com.toir.repository.repair.RepairCampaignWorkItemWindowRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairCampaignShutdownLinkServiceTest {
    @Mock RepairCampaignRepository campaignRepository;
    @Mock PlannedShutdownRepository shutdownRepository;
    @Mock PlannedShutdownCampaignLinkRepository linkRepository;
    @Mock RepairCampaignWorkItemWindowRepository windowRepository;
    @Mock RepairCampaignWorkItemRepository campaignItemRepository;
    @Mock PlannedShutdownWorkItemRepository shutdownItemRepository;
    @Mock ScopeAccessService scopeAccessService;
    @Mock AuditBuilderService audit;
    @InjectMocks RepairCampaignShutdownLinkService service;

    UUID campaignId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID shutdownId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    RepairCampaign campaign;
    PlannedShutdown shutdown;

    @BeforeEach
    void setUp() {
        campaign = new RepairCampaign(); campaign.setId(campaignId); campaign.setVersion(2L);
        campaign.setDepartmentId(UUID.randomUUID()); campaign.setStatus(RepairCampaignStatus.DRAFT);
        shutdown = new PlannedShutdown(); shutdown.setId(shutdownId); shutdown.setVersion(3L);
        shutdown.setDepartmentId(campaign.getDepartmentId()); shutdown.setStatus(PlannedShutdownStatus.SCOPE_FORMATION);
        lenient().when(campaignRepository.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        lenient().when(shutdownRepository.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        lenient().when(campaignRepository.saveAndFlush(campaign)).thenAnswer(inv -> { campaign.setVersion(3L); return campaign; });
        lenient().when(shutdownRepository.saveAndFlush(shutdown)).thenAnswer(inv -> { shutdown.setVersion(4L); return shutdown; });
    }

    @Test
    void linkLocksInSortedUuidOrderReturnsBothPostFlushVersionsAndAuditsBothRoots() {
        when(linkRepository.findByPlannedShutdownIdAndRepairCampaignId(shutdownId, campaignId)).thenReturn(Optional.empty());
        when(linkRepository.saveAndFlush(any())).thenAnswer(inv -> {
            PlannedShutdownCampaignLink link = inv.getArgument(0); link.setId(UUID.randomUUID()); return link;
        });

        var result = service.link(campaignId, shutdownId, new RepairCampaignShutdownLinkRequest(2L, 3L));

        assertThat(result.repairCampaignVersion()).isEqualTo(3L);
        assertThat(result.plannedShutdownVersion()).isEqualTo(4L);
        InOrder order = inOrder(campaignRepository, shutdownRepository);
        order.verify(campaignRepository).findLockedByIdAndIsDeletedFalse(campaignId);
        order.verify(shutdownRepository).findByIdAndIsDeletedFalseForUpdate(shutdownId);
        verify(audit, times(2)).log(eq("repair_campaign_shutdown_link"), any(), eq(AuditAction.CREATE),
                any(), any(), isNull(), eq(result));
    }

    @Test
    void duplicateActiveLinkIsRejectedButDeletedLinkIsSafelyReactivated() {
        PlannedShutdownCampaignLink link = link(false);
        when(linkRepository.findByPlannedShutdownIdAndRepairCampaignId(shutdownId, campaignId))
                .thenReturn(Optional.of(link));
        assertThatThrownBy(() -> service.link(campaignId, shutdownId,
                new RepairCampaignShutdownLinkRequest(2L, 3L))).hasMessageContaining("DUPLICATE");

        link.setDeleted(true);
        when(linkRepository.saveAndFlush(link)).thenReturn(link);
        assertThat(service.link(campaignId, shutdownId,
                new RepairCampaignShutdownLinkRequest(2L, 3L)).active()).isTrue();
        assertThat(link.isDeleted()).isFalse();
    }

    @Test
    void targetDepartmentPbacAndStaleEitherVersionFailClosed() {
        doThrow(new AccessDeniedException("scope")).when(scopeAccessService)
                .assertCanAccessDepartment(shutdown.getDepartmentId());
        assertThatThrownBy(() -> service.link(campaignId, shutdownId,
                new RepairCampaignShutdownLinkRequest(2L, 3L))).isInstanceOf(AccessDeniedException.class);
        reset(scopeAccessService);
        assertThatThrownBy(() -> service.link(campaignId, shutdownId,
                new RepairCampaignShutdownLinkRequest(1L, 3L))).hasMessageContaining("VERSION_CONFLICT");
    }

    @Test
    void canonicalWindowRequiresSameNonManualSourceAndEquipment() {
        PlannedShutdownCampaignLink link = link(false);
        when(linkRepository.findByPlannedShutdownIdAndRepairCampaignIdAndIsDeletedFalse(shutdownId, campaignId))
                .thenReturn(Optional.of(link));
        UUID campaignItemId = UUID.randomUUID(); UUID shutdownItemId = UUID.randomUUID();
        RepairCampaignWorkItem campaignItem = new RepairCampaignWorkItem(); campaignItem.setId(campaignItemId);
        campaignItem.setCampaign(campaign); campaignItem.setSourceType(RepairCampaignWorkItemSourceType.DEFECT);
        campaignItem.setSourceId(UUID.randomUUID()); campaignItem.setEquipmentId(UUID.randomUUID());
        PlannedShutdownWorkItem shutdownItem = new PlannedShutdownWorkItem(); shutdownItem.setId(shutdownItemId);
        shutdownItem.setSourceType(PlannedShutdownWorkItemSourceType.DEFECT);
        shutdownItem.setSourceId(campaignItem.getSourceId()); shutdownItem.setEquipmentId(UUID.randomUUID());
        when(campaignItemRepository.findByIdAndCampaignIdAndIsDeletedFalse(campaignItemId, campaignId))
                .thenReturn(Optional.of(campaignItem));
        when(shutdownItemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(shutdownItemId, shutdownId))
                .thenReturn(Optional.of(shutdownItem));

        assertThatThrownBy(() -> service.addWindow(campaignId, shutdownId,
                new RepairCampaignWorkItemWindowRequest(2L, 3L, campaignItemId, shutdownItemId)))
                .hasMessageContaining("IDENTITY_MISMATCH");
    }

    @Test
    void postStartUnlinkIsRejected() {
        shutdown.setStatus(PlannedShutdownStatus.SHUTDOWN_STARTED);
        assertThatThrownBy(() -> service.unlink(campaignId, shutdownId,
                new RepairCampaignShutdownLinkRequest(2L, 3L)))
                .isInstanceOf(RestException.class).hasMessageContaining("AFTER_START");
        verify(linkRepository, never()).saveAndFlush(any());
    }

    @Test
    void onlyNamedActivePairConstraintIsClassifiedAsDuplicate() {
        when(linkRepository.findByPlannedShutdownIdAndRepairCampaignId(shutdownId, campaignId))
                .thenReturn(Optional.empty());
        var expected = new DataIntegrityViolationException("some_other_constraint");
        when(linkRepository.saveAndFlush(any())).thenThrow(expected);

        assertThatThrownBy(() -> service.link(campaignId, shutdownId,
                new RepairCampaignShutdownLinkRequest(2L, 3L))).isSameAs(expected);

        reset(linkRepository);
        when(linkRepository.findByPlannedShutdownIdAndRepairCampaignId(shutdownId, campaignId))
                .thenReturn(Optional.empty());
        when(linkRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("uq_planned_shutdown_campaigns_active_pair"));
        assertThatThrownBy(() -> service.link(campaignId, shutdownId,
                new RepairCampaignShutdownLinkRequest(2L, 3L))).hasMessageContaining("DUPLICATE");
    }

    @Test
    void listsActiveLinksFromEitherRootInRepositoryOrderWithBothCurrentVersionsAndPbac() {
        PlannedShutdownCampaignLink link = link(false);
        when(campaignRepository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));
        when(shutdownRepository.findByIdAndIsDeletedFalse(shutdownId)).thenReturn(Optional.of(shutdown));
        when(linkRepository.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPlannedShutdownId(campaignId))
                .thenReturn(List.of(link));
        when(linkRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByRepairCampaignId(shutdownId))
                .thenReturn(List.of(link));

        assertThat(service.listForCampaign(campaignId, 2L)).singleElement().satisfies(row -> {
            assertThat(row.repairCampaignId()).isEqualTo(campaignId);
            assertThat(row.plannedShutdownId()).isEqualTo(shutdownId);
            assertThat(row.repairCampaignVersion()).isEqualTo(2L);
            assertThat(row.plannedShutdownVersion()).isEqualTo(3L);
        });
        assertThat(service.listForShutdown(shutdownId, 3L)).containsExactlyElementsOf(
                service.listForCampaign(campaignId, 2L));
        verify(scopeAccessService, atLeast(2)).assertCanAccessDepartment(campaign.getDepartmentId());
        verify(scopeAccessService, atLeast(2)).assertCanAccessDepartment(shutdown.getDepartmentId());
    }

    @Test
    void unlinkAuditsImmutableActiveBeforeAndInactiveAfterWithExactIdentityAndAdvancedVersions() {
        PlannedShutdownCampaignLink link = link(false);
        when(linkRepository.findByPlannedShutdownIdAndRepairCampaignIdAndIsDeletedFalse(shutdownId, campaignId))
                .thenReturn(Optional.of(link));
        when(linkRepository.saveAndFlush(link)).thenReturn(link);

        var result = service.unlink(campaignId, shutdownId, new RepairCampaignShutdownLinkRequest(2L, 3L));

        var before = org.mockito.ArgumentCaptor.forClass(Object.class);
        var after = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(audit, times(2)).log(eq("repair_campaign_shutdown_link"), any(), eq(AuditAction.DELETE),
                any(), contains(link.getId().toString()), before.capture(), after.capture());
        assertThat(before.getAllValues()).allSatisfy(value -> {
            var snapshot = (RepairCampaignShutdownLinkResponse) value;
            assertThat(snapshot.id()).isEqualTo(link.getId());
            assertThat(snapshot.repairCampaignId()).isEqualTo(campaignId);
            assertThat(snapshot.plannedShutdownId()).isEqualTo(shutdownId);
            assertThat(snapshot.active()).isTrue();
            assertThat(snapshot.repairCampaignVersion()).isEqualTo(2L);
            assertThat(snapshot.plannedShutdownVersion()).isEqualTo(3L);
        });
        assertThat(after.getAllValues()).allSatisfy(value -> assertThat(value).isEqualTo(result));
        assertThat(result.active()).isFalse();
        assertThat(result.repairCampaignVersion()).isEqualTo(3L);
        assertThat(result.plannedShutdownVersion()).isEqualTo(4L);
    }

    @Test
    void reverseUuidOrderingLocksShutdownBeforeCampaignAndStillAdvancesBothRoots() {
        UUID highCampaignId = UUID.fromString("70000000-0000-0000-0000-000000000000");
        UUID lowShutdownId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        campaign.setId(highCampaignId); shutdown.setId(lowShutdownId);
        when(shutdownRepository.findByIdAndIsDeletedFalseForUpdate(lowShutdownId)).thenReturn(Optional.of(shutdown));
        when(campaignRepository.findLockedByIdAndIsDeletedFalse(highCampaignId)).thenReturn(Optional.of(campaign));
        when(linkRepository.findByPlannedShutdownIdAndRepairCampaignId(lowShutdownId, highCampaignId))
                .thenReturn(Optional.empty());
        when(linkRepository.saveAndFlush(any())).thenAnswer(inv -> {
            PlannedShutdownCampaignLink value = inv.getArgument(0); value.setId(UUID.randomUUID()); return value;
        });

        var result = service.link(highCampaignId, lowShutdownId,
                new RepairCampaignShutdownLinkRequest(2L, 3L));

        InOrder order = inOrder(campaignRepository, shutdownRepository);
        order.verify(shutdownRepository).findByIdAndIsDeletedFalseForUpdate(lowShutdownId);
        order.verify(campaignRepository).findLockedByIdAndIsDeletedFalse(highCampaignId);
        assertThat(result.repairCampaignVersion()).isEqualTo(3L);
        assertThat(result.plannedShutdownVersion()).isEqualTo(4L);
    }

    @Test
    void removeWindowAuditsImmutableActiveBeforeAndInactiveAfterWithExactItemIds() {
        UUID windowId = UUID.randomUUID(); UUID campaignItemId = UUID.randomUUID(); UUID shutdownItemId = UUID.randomUUID();
        RepairCampaignWorkItemWindow window = new RepairCampaignWorkItemWindow();
        window.setId(windowId); window.setRepairCampaignId(campaignId);
        window.setRepairCampaignWorkItemId(campaignItemId); window.setPlannedShutdownId(shutdownId);
        window.setShutdownWorkItemId(shutdownItemId);
        when(windowRepository.findByIdAndRepairCampaignIdAndPlannedShutdownIdAndIsDeletedFalse(
                windowId, campaignId, shutdownId)).thenReturn(Optional.of(window));
        when(windowRepository.saveAndFlush(window)).thenReturn(window);

        var result = service.removeWindow(campaignId, shutdownId, windowId,
                new RepairCampaignShutdownLinkRequest(2L, 3L));

        var before = org.mockito.ArgumentCaptor.forClass(Object.class);
        var after = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(audit, times(2)).log(eq("repair_campaign_shutdown_link"), any(), eq(AuditAction.DELETE),
                any(), contains(windowId.toString()), before.capture(), after.capture());
        assertThat(before.getAllValues()).allSatisfy(value -> {
            var snapshot = (RepairCampaignWorkItemWindowResponse) value;
            assertThat(snapshot.id()).isEqualTo(windowId);
            assertThat(snapshot.repairCampaignWorkItemId()).isEqualTo(campaignItemId);
            assertThat(snapshot.shutdownWorkItemId()).isEqualTo(shutdownItemId);
            assertThat(snapshot.active()).isTrue();
            assertThat(snapshot.repairCampaignVersion()).isEqualTo(2L);
            assertThat(snapshot.plannedShutdownVersion()).isEqualTo(3L);
        });
        assertThat(after.getAllValues()).allSatisfy(value -> assertThat(value).isEqualTo(result));
        assertThat(result.active()).isFalse();
        assertThat(result.repairCampaignVersion()).isEqualTo(3L);
        assertThat(result.plannedShutdownVersion()).isEqualTo(4L);
    }

    private PlannedShutdownCampaignLink link(boolean deleted) {
        PlannedShutdownCampaignLink link = new PlannedShutdownCampaignLink(); link.setId(UUID.randomUUID());
        link.setRepairCampaignId(campaignId); link.setPlannedShutdownId(shutdownId); link.setDeleted(deleted); return link;
    }
}
