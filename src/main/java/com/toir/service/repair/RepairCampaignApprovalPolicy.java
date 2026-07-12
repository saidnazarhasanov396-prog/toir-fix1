package com.toir.service.repair;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class RepairCampaignApprovalPolicy {

    private static final List<String> DISCIPLINE_ROLES = List.of(
            "REPAIR_CAMPAIGN_CHIEF_MECHANIC_APPROVER", "REPAIR_CAMPAIGN_PRODUCTION_APPROVER",
            "REPAIR_CAMPAIGN_WAREHOUSE_APPROVER", "REPAIR_CAMPAIGN_PROCUREMENT_APPROVER",
            "REPAIR_CAMPAIGN_FINANCE_APPROVER", "REPAIR_CAMPAIGN_HSE_APPROVER",
            "REPAIR_CAMPAIGN_CHIEF_ENGINEER_APPROVER");

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
        if (request == null
                || campaign.getStatus() != RepairCampaignStatus.PENDING_APPROVAL
                || campaign.getApprovalScopeHash() == null
                || !Objects.equals(campaign.getScopeVersion(), campaign.getApprovalScopeVersion())
                || !Objects.equals(campaign.getApprovalScopeHash(), scopeHasher.hash(campaign))
                || !Objects.equals(request.getPayloadJson(), payload(campaign))
                || !completedSevenDisciplineRoute(request)) {
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SCOPE_STALE");
        }
    }

    private boolean completedSevenDisciplineRoute(ApprovalRequest request) {
        if (request == null || request.getStatus() != com.toir.enums.ApprovalStatus.APPROVED
                || request.getActionType() != com.toir.enums.ApprovalActionType.APPROVE
                || request.getSteps() == null || request.getSteps().size() != DISCIPLINE_ROLES.size()) return false;
        boolean ordered = IntStream.range(0, DISCIPLINE_ROLES.size()).allMatch(index -> {
            var step = request.getSteps().get(index);
            return step.getStepNumber() == index + 1
                    && DISCIPLINE_ROLES.get(index).equals(step.getApproverRole())
                    && step.getDecision() == com.toir.enums.ApprovalDecision.APPROVED
                    && step.getDecidedById() != null;
        });
        if (!ordered) return false;
        Set<java.util.UUID> actors = request.getSteps().stream()
                .map(com.toir.entity.ApprovalStep::getDecidedById).collect(java.util.stream.Collectors.toSet());
        return actors.size() == DISCIPLINE_ROLES.size() && !actors.contains(request.getRequesterId());
    }

    public static String payload(RepairCampaign campaign) {
        return "{\"scopeVersion\":" + campaign.getApprovalScopeVersion()
                + ",\"scopeHash\":\"" + campaign.getApprovalScopeHash()
                + "\",\"campaignVersion\":" + campaign.getVersion() + "}";
    }
}
