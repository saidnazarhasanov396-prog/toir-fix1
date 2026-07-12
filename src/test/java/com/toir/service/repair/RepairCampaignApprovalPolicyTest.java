package com.toir.service.repair;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepairCampaignApprovalPolicyTest {

    @Test
    void explicitRequestCapturesScopeAndIsTheOnlyOperationEnteringPendingApproval() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = new RepairCampaign();
        campaign.setStatus(RepairCampaignStatus.RESOURCE_CHECK);
        campaign.setScopeVersion(4L);
        when(hasher.hash(campaign)).thenReturn("a".repeat(64));

        new RepairCampaignApprovalPolicy(hasher).prepareRequest(campaign, 4L);

        assertThat(campaign.getStatus()).isEqualTo(RepairCampaignStatus.PENDING_APPROVAL);
        assertThat(campaign.getApprovalScopeVersion()).isEqualTo(4L);
        assertThat(campaign.getApprovalScopeHash()).isEqualTo("a".repeat(64));
    }

    @Test
    void decisionRejectsStaleHashVersionOrPayload() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = new RepairCampaign();
        campaign.setStatus(RepairCampaignStatus.PENDING_APPROVAL);
        campaign.setScopeVersion(5L);
        campaign.setApprovalScopeVersion(4L);
        campaign.setApprovalScopeHash("a".repeat(64));
        ApprovalRequest request = new ApprovalRequest();
        request.setPayloadJson("{}");

        assertThatThrownBy(() -> new RepairCampaignApprovalPolicy(hasher).validateDecision(campaign, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("REPAIR_CAMPAIGN_APPROVAL_SCOPE_STALE");
    }

    @Test
    void decisionAcceptsOnlyCurrentHashScopeAndCampaignVersionPayload() {
        RepairCampaignApprovalScopeHasher hasher = mock(RepairCampaignApprovalScopeHasher.class);
        RepairCampaign campaign = new RepairCampaign();
        campaign.setStatus(RepairCampaignStatus.PENDING_APPROVAL);
        campaign.setScopeVersion(5L);
        campaign.setApprovalScopeVersion(5L);
        campaign.setApprovalScopeHash("b".repeat(64));
        campaign.setVersion(9L);
        when(hasher.hash(campaign)).thenReturn("b".repeat(64));
        ApprovalRequest request = new ApprovalRequest();
        request.setPayloadJson(RepairCampaignApprovalPolicy.payload(campaign));

        new RepairCampaignApprovalPolicy(hasher).validateDecision(campaign, request);
        request.setPayloadJson(request.getPayloadJson().replace(":9}", ":8}"));
        assertThatThrownBy(() -> new RepairCampaignApprovalPolicy(hasher).validateDecision(campaign, request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("REPAIR_CAMPAIGN_APPROVAL_SCOPE_STALE");
    }
}
