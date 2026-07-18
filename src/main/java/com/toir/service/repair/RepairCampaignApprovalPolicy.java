package com.toir.service.repair;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.exception.RestException;
import com.toir.service.approval.LifecycleApprovalRoutePolicy;
import com.toir.service.approval.LifecycleApprovalStartPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Objects;

@Component
public class RepairCampaignApprovalPolicy {

    private static final Logger log = LoggerFactory.getLogger(RepairCampaignApprovalPolicy.class);

    private final RepairCampaignApprovalScopeHasher scopeHasher;
    private final LifecycleApprovalRoutePolicy lifecyclePolicy;

    @Autowired
    public RepairCampaignApprovalPolicy(RepairCampaignApprovalScopeHasher scopeHasher,
                                        LifecycleApprovalRoutePolicy lifecyclePolicy) {
        this.scopeHasher = scopeHasher;
        this.lifecyclePolicy = lifecyclePolicy;
    }

    public RepairCampaignApprovalPolicy(RepairCampaignApprovalScopeHasher scopeHasher) {
        this(scopeHasher, new LifecycleApprovalRoutePolicy());
    }

    public String validateRequestScope(RepairCampaign campaign, Long expectedScopeVersion) {
        long scopeVersion = campaign.getScopeVersion() == null ? 0L : campaign.getScopeVersion();
        if (expectedScopeVersion == null || expectedScopeVersion != scopeVersion) {
            throw RestException.conflict("REPAIR_CAMPAIGN_STALE_SCOPE_VERSION");
        }
        if (campaign.getStatus() != RepairCampaignStatus.RESOURCE_CHECK
                && campaign.getStatus() != RepairCampaignStatus.PENDING_APPROVAL) {
            throw RestException.conflict("REPAIR_CAMPAIGN_NOT_READY_FOR_APPROVAL");
        }
        String scopeHash = scopeHasher.hash(campaign);
        if (campaign.getStatus() == RepairCampaignStatus.PENDING_APPROVAL) {
            if (!Objects.equals(campaign.getScopeVersion(), campaign.getApprovalScopeVersion())) {
                throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SCOPE_VERSION_MISMATCH");
            }
            if (!Objects.equals(scopeHash, campaign.getApprovalScopeHash())) {
                throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SCOPE_HASH_MISMATCH");
            }
        }
        return scopeHash;
    }

    public void prepareRequest(RepairCampaign campaign, Long expectedScopeVersion) {
        if (campaign.getStatus() != RepairCampaignStatus.RESOURCE_CHECK) {
            throw RestException.conflict("REPAIR_CAMPAIGN_NOT_READY_FOR_APPROVAL");
        }
        String scopeHash = validateRequestScope(campaign, expectedScopeVersion);
        long scopeVersion = campaign.getScopeVersion() == null ? 0L : campaign.getScopeVersion();
        campaign.setApprovalScopeVersion(scopeVersion);
        campaign.setApprovalScopeHash(scopeHash);
        campaign.setStatus(RepairCampaignStatus.PENDING_APPROVAL);
    }

    public void requireStartPlan(LifecycleApprovalStartPlan plan) {
        if (plan != null && (plan.reusable() || plan.creatable())) {
            return;
        }
        LifecycleApprovalRoutePolicy.Reason reason = plan == null ? null : plan.failure();
        if (reason == LifecycleApprovalRoutePolicy.Reason.NO_ACTIVE_TEMPLATE) {
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_TEMPLATE_NOT_CONFIGURED");
        }
        if (reason == LifecycleApprovalRoutePolicy.Reason.MULTIPLE_ACTIVE_TEMPLATES) {
            throw RestException.conflict("MULTIPLE_ACTIVE_TEMPLATES");
        }
        if (isInvalidTemplate(reason)) {
            throw RestException.conflict("APPROVAL_TEMPLATE_STEPS_INVALID");
        }
        throw routeStale(reason == null ? "approval start plan is null" : reason.name());
    }

    private static boolean isInvalidTemplate(LifecycleApprovalRoutePolicy.Reason reason) {
        return reason == LifecycleApprovalRoutePolicy.Reason.EMPTY_ROUTE
                || reason == LifecycleApprovalRoutePolicy.Reason.NONPOSITIVE_ORDER
                || reason == LifecycleApprovalRoutePolicy.Reason.DUPLICATE_ORDER
                || reason == LifecycleApprovalRoutePolicy.Reason.NONCONTIGUOUS_ORDER
                || reason == LifecycleApprovalRoutePolicy.Reason.INVALID_ASSIGNMENT
                || reason == LifecycleApprovalRoutePolicy.Reason.DUPLICATE_EXPLICIT_APPROVER;
    }

    public void validateCompletion(RepairCampaign campaign, ApprovalRequest request) {
        if (request == null) {
            throw routeStale("approval request is null");
        }
        if (campaign.getStatus() != RepairCampaignStatus.PENDING_APPROVAL) {
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_CAMPAIGN_STATUS_MISMATCH");
        }
        ApprovalTargetType effectiveTarget = request.getTargetType() == null
                ? ApprovalTargetType.fromDocumentType(request.getDocumentType())
                : request.getTargetType();
        java.util.UUID effectiveTargetId = request.getTargetId() == null
                ? request.getDocumentId()
                : request.getTargetId();
        ApprovalActionType effectiveAction = request.getActionType() == null
                ? ApprovalActionType.APPROVE
                : request.getActionType();
        if (effectiveTarget != ApprovalTargetType.REPAIR_CAMPAIGN
                || !Objects.equals(effectiveTargetId, campaign.getId())
                || effectiveAction != ApprovalActionType.APPROVE) {
            throw routeStale("approval request target/action mismatch");
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
        if (request.getStatus() != ApprovalStatus.APPROVED) {
            throw RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_INCOMPLETE_DISCIPLINE_ROUTE");
        }
        LifecycleApprovalRoutePolicy.ValidationResult result = lifecyclePolicy.validateCompletion(
                normalizedCompletionView(request, effectiveTarget, effectiveTargetId, effectiveAction));
        if (!result.valid()) {
            throw mapRuntimeCompletionFailure(result.reason());
        }
    }

    public void validateDecision(RepairCampaign campaign, ApprovalRequest request) {
        validateCompletion(campaign, request);
    }

    private ApprovalRequest normalizedCompletionView(ApprovalRequest request,
                                                     ApprovalTargetType effectiveTarget,
                                                     java.util.UUID effectiveTargetId,
                                                     ApprovalActionType effectiveAction) {
        if (request.getTargetType() != null
                && request.getTargetId() != null
                && request.getActionType() != null) {
            return request;
        }
        ApprovalRequest normalized = new ApprovalRequest();
        normalized.setTargetType(effectiveTarget);
        normalized.setTargetId(effectiveTargetId);
        normalized.setActionType(effectiveAction);
        normalized.setRequesterId(request.getRequesterId());
        normalized.setStatus(request.getStatus());
        normalized.setCurrentStep(request.getCurrentStep());
        normalized.setSteps(request.getSteps() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getSteps()));
        return normalized;
    }

    private RestException mapRuntimeCompletionFailure(LifecycleApprovalRoutePolicy.Reason reason) {
        if (reason == LifecycleApprovalRoutePolicy.Reason.RUNTIME_INCOMPLETE
                || reason == LifecycleApprovalRoutePolicy.Reason.DECISION_ACTOR_MISSING) {
            return RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_INCOMPLETE_DISCIPLINE_ROUTE");
        }
        if (reason == LifecycleApprovalRoutePolicy.Reason.REQUESTER_DECISION
                || reason == LifecycleApprovalRoutePolicy.Reason.REPEATED_APPROVING_ACTOR) {
            return RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_SEPARATION_OF_DUTY_FAILURE");
        }
        return routeStale(reason == null ? "runtime completion reason is null" : reason.name());
    }

    private RestException routeStale(String detail) {
        log.warn("Repair campaign approval route stale: {}", detail);
        return RestException.conflict("REPAIR_CAMPAIGN_APPROVAL_ROUTE_STALE");
    }

    public static String payload(RepairCampaign campaign) {
        return "{\"scopeVersion\":" + campaign.getApprovalScopeVersion()
                + ",\"scopeHash\":\"" + campaign.getApprovalScopeHash()
                + "\",\"campaignVersion\":" + campaign.getVersion() + "}";
    }
}
