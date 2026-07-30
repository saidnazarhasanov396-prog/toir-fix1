package com.toir.service;

import com.toir.enums.PlanStatus;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PprPlanVisibilityPolicy {

    private static final Set<PlanStatus> WORKER_VISIBLE_STATUSES = Set.copyOf(EnumSet.of(
            PlanStatus.APPROVED,
            PlanStatus.IN_PROGRESS,
            PlanStatus.CLOSED,
            PlanStatus.CANCELLED
    ));

    private final ScopeAccessService scopeAccessService;

    public PprPlanVisibilityPolicy(ScopeAccessService scopeAccessService) {
        this.scopeAccessService = scopeAccessService;
    }

    public boolean canViewUnapprovedPlans() {
        return scopeAccessService.isScopeAdmin()
                || scopeAccessService.hasAuthority(PermissionConstants.PPR_CALENDAR_CREATE)
                || scopeAccessService.hasAuthority(PermissionConstants.PPR_CALENDAR_UPDATE)
                || scopeAccessService.hasAuthority(PermissionConstants.PPR_CALENDAR_DELETE)
                || scopeAccessService.hasAuthority(PermissionConstants.PPR_CALENDAR_GENERATE)
                || scopeAccessService.hasAuthority(PermissionConstants.PPR_CALENDAR_APPROVE);
    }

    public Set<PlanStatus> visibleStatuses(Collection<PlanStatus> requestedStatuses) {
        Set<PlanStatus> requested = requestedStatuses == null || requestedStatuses.isEmpty()
                ? EnumSet.allOf(PlanStatus.class)
                : EnumSet.copyOf(requestedStatuses);
        if (canViewUnapprovedPlans()) {
            return Set.copyOf(requested);
        }
        requested.retainAll(WORKER_VISIBLE_STATUSES);
        return Set.copyOf(requested);
    }

    public boolean isVisible(PlanStatus status) {
        return status != null
                && (canViewUnapprovedPlans() || WORKER_VISIBLE_STATUSES.contains(status));
    }
}
