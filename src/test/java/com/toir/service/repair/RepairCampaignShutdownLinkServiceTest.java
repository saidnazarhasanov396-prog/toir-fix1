package com.toir.service.repair;

import com.toir.dto.repaircampaign.*;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.plannedshutdown.PlannedShutdownCampaignLink;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignWorkItem;
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

    private PlannedShutdownCampaignLink link(boolean deleted) {
        PlannedShutdownCampaignLink link = new PlannedShutdownCampaignLink(); link.setId(UUID.randomUUID());
        link.setRepairCampaignId(campaignId); link.setPlannedShutdownId(shutdownId); link.setDeleted(deleted); return link;
    }
}
