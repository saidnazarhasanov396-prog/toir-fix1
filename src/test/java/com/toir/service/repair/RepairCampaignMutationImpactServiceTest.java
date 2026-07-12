package com.toir.service.repair;

import com.toir.dto.repaircampaign.CampaignMutationImpact;
import com.toir.enums.RepairCampaignMutationType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.entity.ApprovalRequest;
import com.toir.entity.repair.RepairCampaign;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepairCampaignMutationImpactServiceTest {

    @Test
    void everyApprovedMutationInvalidatesAndReturnsToItsDeterministicFormationStage() {
        EnumSet<RepairCampaignMutationType> scopeMutations = EnumSet.of(
                RepairCampaignMutationType.METADATA,
                RepairCampaignMutationType.DATES,
                RepairCampaignMutationType.WORK_ITEMS,
                RepairCampaignMutationType.SHUTDOWN_LINKS);

        for (RepairCampaignMutationType type : RepairCampaignMutationType.values()) {
            CampaignMutationImpact impact = RepairCampaignMutationImpactService.evaluate(
                    RepairCampaignStatus.APPROVED, 3L, 9L, type);
            assertThat(impact.invalidatesApproval()).isTrue();
            assertThat(impact.currentScopeVersion()).isEqualTo(3L);
            assertThat(impact.currentVersion()).isEqualTo(9L);
            assertThat(impact.nextStatus()).isEqualTo(scopeMutations.contains(type)
                    ? RepairCampaignStatus.SCOPE_FORMATION
                    : RepairCampaignStatus.RESOURCE_CHECK);
            assertThat(impact.reason()).isEqualTo("REPAIR_CAMPAIGN_APPROVAL_SCOPE_INVALIDATED");
        }
    }

    @Test
    void preApprovalMutationAdvancesScopeWithoutInventingPendingApproval() {
        CampaignMutationImpact impact = RepairCampaignMutationImpactService.evaluate(
                RepairCampaignStatus.RESOURCE_CHECK, 2L, 4L, RepairCampaignMutationType.MATERIALS);

        assertThat(impact.invalidatesApproval()).isFalse();
        assertThat(impact.nextStatus()).isEqualTo(RepairCampaignStatus.RESOURCE_CHECK);
        assertThat(impact.reason()).isEqualTo("REPAIR_CAMPAIGN_SCOPE_CHANGED");
    }

    @Test
    void scopeOnlyAuthorityCannotPreviewFinanceMutation() {
        RepairCampaignRepository campaigns = mock(RepairCampaignRepository.class);
        ScopeAccessService scope = mock(ScopeAccessService.class);
        ApprovalRequestRepository approvals = mock(ApprovalRequestRepository.class);
        RepairCampaignMutationImpactService service = new RepairCampaignMutationImpactService(campaigns, scope, approvals);
        RepairCampaign campaign = approvedCampaign();
        when(scope.hasAuthority(com.toir.security.PermissionConstants.REPAIR_CAMPAIGN_MANAGE_SCOPE)).thenReturn(true);

        assertThatThrownBy(() -> service.preview(campaign, RepairCampaignMutationType.BUDGET, 9L, 3L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void previewAndCommitUseIdenticalImpactAndCommitRetiresApprovalFacts() {
        RepairCampaignRepository campaigns = mock(RepairCampaignRepository.class);
        ScopeAccessService scope = mock(ScopeAccessService.class);
        ApprovalRequestRepository approvals = mock(ApprovalRequestRepository.class);
        RepairCampaignMutationImpactService service = new RepairCampaignMutationImpactService(campaigns, scope, approvals);
        RepairCampaign campaign = approvedCampaign();
        ApprovalRequest pending = new ApprovalRequest();
        pending.setStatus(com.toir.enums.ApprovalStatus.PENDING);
        when(scope.hasAuthority(com.toir.security.PermissionConstants.REPAIR_CAMPAIGN_MANAGE_FINANCE)).thenReturn(true);
        when(approvals.findAllByTargetTypeAndTargetIdAndIsDeletedFalse("REPAIR_CAMPAIGN", campaign.getId()))
                .thenReturn(List.of(pending));

        CampaignMutationImpact preview = service.preview(campaign, RepairCampaignMutationType.FX, 9L, 3L);
        CampaignMutationImpact committed = service.apply(campaign, RepairCampaignMutationType.FX);

        assertThat(committed).isEqualTo(preview);
        assertThat(campaign.getScopeVersion()).isEqualTo(4L);
        assertThat(campaign.getStatus()).isEqualTo(RepairCampaignStatus.RESOURCE_CHECK);
        assertThat(campaign.getApprovalScopeVersion()).isNull();
        assertThat(campaign.getApprovalScopeHash()).isNull();
        assertThat(campaign.getApprovedAt()).isNull();
        assertThat(pending.getStatus()).isEqualTo(com.toir.enums.ApprovalStatus.CANCELLED);
        verify(approvals).save(pending);
    }

    private RepairCampaign approvedCampaign() {
        RepairCampaign campaign = new RepairCampaign();
        campaign.setId(UUID.randomUUID());
        campaign.setStatus(RepairCampaignStatus.APPROVED);
        campaign.setVersion(9L);
        campaign.setScopeVersion(3L);
        campaign.setApprovalScopeVersion(3L);
        campaign.setApprovalScopeHash("hash");
        campaign.setApprovedAt(java.time.Instant.now());
        return campaign;
    }
}
