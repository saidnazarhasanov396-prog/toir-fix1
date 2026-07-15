package com.toir.service.repair;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class RepairCampaignApprovalPolicy {

    private static final Logger log = LoggerFactory.getLogger(RepairCampaignApprovalPolicy.class);

    public static final List<String> DISCIPLINE_ROLES = RepairCampaignApprovalRouteValidator.REQUIRED_DISCIPLINE_ROLES;

    private final RepairCampaignApprovalScopeHasher scopeHasher;
    private final RepairCampaignApprovalRouteValidator routeValidator;

    @Autowired
    public RepairCampaignApprovalPolicy(RepairCampaignApprovalScopeHasher scopeHasher,
                                        RepairCampaignApprovalRouteValidator routeValidator) {
        this.scopeHasher = scopeHasher;
        this.routeValidator = routeValidator;
    }

    public RepairCampaignApprovalPolicy(RepairCampaignApprovalScopeHasher scopeHasher) {
        this(scopeHasher, new RepairCampaignApprovalRouteValidator());
    }

    public void prepareRequest(RepairCampaign campaign, Long expectedScopeVersion) {
        long scopeVersion = campaign.getScopeVersion() == null ? 0L : campaign.getScopeVersion();
        if (expectedScopeVersion == null || expectedScopeVersion != scopeVersion) {
            throw RestException.conflict("REPAIR_CAMPAIGN_STALE_SCOPE_VERSION");
        }
        if (campaign.getStatus() != RepairCampaignStatus.RESOURCE_CHECK) {
            throw RestException.conflict("REPAIR_CAMPAIGN_NOT_READY_FOR_APPROVAL");
        }
        String scopeHash = scopeHasher.hash(campaign);
        campaign.setApprovalScopeVersion(scopeVersion);
        campaign.setApprovalScopeHash(scopeHash);
        campaign.setStatus(RepairCampaignStatus.PENDING_APPROVAL);
    }

    public void validateDecision(RepairCampaign campaign, ApprovalRequest request) {
        if (request == null) {
            throw routeStale("approval request is null");
        }
        if (campaign.getStatus() != RepairCampaignStatus.PENDING_APPROVAL) {
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_CAMPAIGN_STATUS_MISMATCH");
        }
        if (campaign.getApprovalScopeHash() == null) {
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SCOPE_HASH_MISMATCH");
        }
        if (!Objects.equals(campaign.getScopeVersion(), campaign.getApprovalScopeVersion())) {
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SCOPE_VERSION_MISMATCH");
        }
        if (!Objects.equals(campaign.getApprovalScopeHash(), scopeHasher.hash(campaign))) {
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SCOPE_HASH_MISMATCH");
        }
        String expectedPayload = payload(campaign);
        if (!Objects.equals(request.getPayloadJson(), expectedPayload)) {
            if (request.getPayloadJson() == null
                    || !request.getPayloadJson().contains("\"campaignVersion\":" + campaign.getVersion())) {
                throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_CAMPAIGN_VERSION_MISMATCH");
            }
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_PAYLOAD_MISMATCH");
        }
        RepairCampaignApprovalRouteValidator.ValidationResult route = routeValidator.validateCompleted(request);
        if (!route.valid()) {
            if (route.reason() == RepairCampaignApprovalRouteValidator.RouteValidationReason.INCOMPLETE_DISCIPLINE_ROUTE) {
                throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_INCOMPLETE_DISCIPLINE_ROUTE");
            }
            if (route.reason() == RepairCampaignApprovalRouteValidator.RouteValidationReason.SEPARATION_OF_DUTY_FAILURE) {
                throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SEPARATION_OF_DUTY_FAILURE");
            }
            throw routeStale(route.detail());
        }
    }

    private RestException routeStale(String detail) {
        log.warn("Repair campaign approval route stale: {}", detail);
        return RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_ROUTE_STALE");
    }

    public static boolean hasCanonicalDisciplineRoute(ApprovalRequest request) {
        return new RepairCampaignApprovalRouteValidator().validate(request).valid();
    }

    public static String payload(RepairCampaign campaign) {
        return "{\"scopeVersion\":" + campaign.getApprovalScopeVersion()
                + ",\"scopeHash\":\"" + campaign.getApprovalScopeHash()
                + "\",\"campaignVersion\":" + campaign.getVersion() + "}";
    }
}
