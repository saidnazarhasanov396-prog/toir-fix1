package com.toir.service.repair;

import com.toir.dto.repaircampaign.CampaignMutationImpact;
import com.toir.enums.RepairCampaignMutationType;
import com.toir.enums.RepairCampaignStatus;
import com.toir.entity.repair.RepairCampaign;
import com.toir.exception.RestException;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.enums.ApprovalStatus;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RepairCampaignMutationImpactService {

    private final RepairCampaignRepository campaignRepository;
    private final ScopeAccessService scopeAccessService;
    private final ApprovalRequestRepository approvalRequestRepository;

    private static final EnumSet<RepairCampaignMutationType> SCOPE_FORMATION_MUTATIONS = EnumSet.of(
            RepairCampaignMutationType.METADATA,
            RepairCampaignMutationType.DATES,
            RepairCampaignMutationType.WORK_ITEMS,
            RepairCampaignMutationType.SHUTDOWN_LINKS);

    private static final EnumSet<RepairCampaignStatus> APPROVAL_BEARING_STATUSES = EnumSet.of(
            RepairCampaignStatus.PENDING_APPROVAL,
            RepairCampaignStatus.APPROVED,
            RepairCampaignStatus.PREPARATION);

    public static CampaignMutationImpact evaluate(
            RepairCampaignStatus currentStatus,
            long currentScopeVersion,
            long currentVersion,
            RepairCampaignMutationType mutationType
    ) {
        Objects.requireNonNull(currentStatus, "currentStatus");
        Objects.requireNonNull(mutationType, "mutationType");
        boolean invalidates = APPROVAL_BEARING_STATUSES.contains(currentStatus);
        RepairCampaignStatus fallback = SCOPE_FORMATION_MUTATIONS.contains(mutationType)
                ? RepairCampaignStatus.SCOPE_FORMATION
                : RepairCampaignStatus.RESOURCE_CHECK;
        return new CampaignMutationImpact(
                invalidates,
                currentStatus,
                currentScopeVersion,
                currentVersion,
                invalidates ? fallback : currentStatus,
                invalidates
                        ? "REPAIR_CAMPAIGN_APPROVAL_SCOPE_INVALIDATED"
                        : "REPAIR_CAMPAIGN_SCOPE_CHANGED",
                List.of());
    }

    public CampaignMutationImpact preview(
            java.util.UUID campaignId,
            RepairCampaignMutationType mutationType,
            Long expectedVersion,
            Long expectedScopeVersion
    ) {
        RepairCampaign campaign = campaignRepository.findByIdAndIsDeletedFalse(campaignId)
                .orElseThrow(() -> RestException.notFound("Repair campaign not found"));
        scopeAccessService.assertCanAccessDepartment(campaign.getDepartmentId());
        assertMutationPermission(mutationType);
        long version = campaign.getVersion() == null ? 0L : campaign.getVersion();
        long scopeVersion = campaign.getScopeVersion() == null ? 0L : campaign.getScopeVersion();
        if (expectedVersion == null || expectedVersion != version) {
            throw RestException.conflict("REPAIR_CAMPAIGN_STALE_VERSION");
        }
        if (expectedScopeVersion == null || expectedScopeVersion != scopeVersion) {
            throw RestException.conflict("REPAIR_CAMPAIGN_STALE_SCOPE_VERSION");
        }
        return evaluate(campaign.getStatus(), scopeVersion, version, mutationType);
    }

    public CampaignMutationImpact apply(RepairCampaign campaign, RepairCampaignMutationType mutationType) {
        long version = campaign.getVersion() == null ? 0L : campaign.getVersion();
        long scopeVersion = campaign.getScopeVersion() == null ? 0L : campaign.getScopeVersion();
        CampaignMutationImpact impact = evaluate(campaign.getStatus(), scopeVersion, version, mutationType);
        campaign.setScopeVersion(scopeVersion + 1L);
        if (impact.invalidatesApproval()) {
            approvalRequestRepository.findAllByTargetTypeAndTargetIdAndIsDeletedFalse(
                            "REPAIR_CAMPAIGN", campaign.getId()).stream()
                    .filter(request -> request.getStatus() == ApprovalStatus.PENDING)
                    .forEach(request -> {
                        request.setStatus(ApprovalStatus.CANCELLED);
                        request.setCompletedAt(java.time.Instant.now());
                        approvalRequestRepository.save(request);
                    });
            campaign.setApprovalScopeVersion(null);
            campaign.setApprovalScopeHash(null);
            campaign.setApprovedAt(null);
            campaign.setStatus(impact.nextStatus());
        }
        return impact;
    }

    public String requiredPermission(RepairCampaignMutationType mutationType) {
        return switch (mutationType) {
            case METADATA, DATES -> PermissionConstants.REPAIR_CAMPAIGN_MANAGE_SCOPE;
            case WORK_ITEMS -> PermissionConstants.REPAIR_CAMPAIGN_MANAGE_WORK;
            case SHUTDOWN_LINKS -> PermissionConstants.REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS;
            case DEPENDENCIES -> PermissionConstants.REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES;
            case RESOURCES -> PermissionConstants.REPAIR_CAMPAIGN_MANAGE_RESOURCES;
            case MATERIALS -> PermissionConstants.REPAIR_CAMPAIGN_MANAGE_MATERIALS;
            case BUDGET, FX -> PermissionConstants.REPAIR_CAMPAIGN_MANAGE_FINANCE;
        };
    }

    private void assertMutationPermission(RepairCampaignMutationType mutationType) {
        String required = requiredPermission(mutationType);
        if (!scopeAccessService.isScopeAdmin() && !scopeAccessService.hasAuthority(required)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
    }
}
