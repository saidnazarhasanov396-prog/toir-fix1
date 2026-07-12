package com.toir.service.repair;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RepairCampaignApprovalPolicy {

    private final RepairCampaignApprovalScopeHasher scopeHasher;

    public void prepareRequest(RepairCampaign campaign, Long expectedScopeVersion) {
        long scopeVersion = campaign.getScopeVersion() == null ? 0L : campaign.getScopeVersion();
        if (expectedScopeVersion == null || expectedScopeVersion != scopeVersion) {
            throw RestException.conflict("REPAIR_CAMPAIGN_STALE_SCOPE_VERSION");
        }
        if (campaign.getStatus() != RepairCampaignStatus.RESOURCE_CHECK) {
            throw RestException.conflict("REPAIR_CAMPAIGN_NOT_READY_FOR_APPROVAL");
        }
        campaign.setApprovalScopeVersion(scopeVersion);
        campaign.setApprovalScopeHash(scopeHasher.hash(campaign));
        campaign.setStatus(RepairCampaignStatus.PENDING_APPROVAL);
    }

    public void validateDecision(RepairCampaign campaign, ApprovalRequest request) {
        if (campaign.getStatus() != RepairCampaignStatus.PENDING_APPROVAL
                || campaign.getApprovalScopeHash() == null
                || !Objects.equals(campaign.getScopeVersion(), campaign.getApprovalScopeVersion())
                || !Objects.equals(campaign.getApprovalScopeHash(), scopeHasher.hash(campaign))
                || !Objects.equals(request.getPayloadJson(), payload(campaign))) {
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SCOPE_STALE");
        }
    }

    public static String payload(RepairCampaign campaign) {
        return "{\"scopeVersion\":" + campaign.getApprovalScopeVersion()
                + ",\"scopeHash\":\"" + campaign.getApprovalScopeHash()
                + "\",\"campaignVersion\":" + campaign.getVersion() + "}";
    }
}
