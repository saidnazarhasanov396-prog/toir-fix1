package com.toir.service;

import com.toir.enums.PlanStatus;
import com.toir.security.ScopeAccessService;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PprPlanVisibilityPolicyTest {

    @Test
    void ordinaryWorkerCannotRequestDraftOrGeneratedPlans() {
        ScopeAccessService scope = mock(ScopeAccessService.class);
        PprPlanVisibilityPolicy policy = new PprPlanVisibilityPolicy(scope);

        assertThat(policy.visibleStatuses(Set.of(PlanStatus.DRAFT, PlanStatus.APPROVED)))
                .containsExactly(PlanStatus.APPROVED);
        assertThat(policy.visibleStatuses(Set.of()))
                .containsExactlyInAnyOrder(
                        PlanStatus.APPROVED,
                        PlanStatus.IN_PROGRESS,
                        PlanStatus.CLOSED,
                        PlanStatus.CANCELLED
                );
    }

    @Test
    void calendarManagersAndAdminCanSeeEveryRequestedStatus() {
        for (String authority : new String[]{
                "PPR_CALENDAR_CREATE",
                "PPR_CALENDAR_UPDATE",
                "PPR_CALENDAR_DELETE",
                "PPR_CALENDAR_GENERATE",
                "PPR_CALENDAR_APPROVE"
        }) {
            ScopeAccessService scope = mock(ScopeAccessService.class);
            when(scope.hasAuthority(authority)).thenReturn(true);
            PprPlanVisibilityPolicy policy = new PprPlanVisibilityPolicy(scope);

            assertThat(policy.visibleStatuses(Set.of(PlanStatus.DRAFT, PlanStatus.GENERATED)))
                    .containsExactlyInAnyOrder(PlanStatus.DRAFT, PlanStatus.GENERATED);
        }

        ScopeAccessService adminScope = mock(ScopeAccessService.class);
        when(adminScope.isScopeAdmin()).thenReturn(true);
        assertThat(new PprPlanVisibilityPolicy(adminScope).visibleStatuses(Set.of()))
                .containsExactlyInAnyOrder(PlanStatus.values());
    }

    @Test
    void registryPlanActionsDoNotExposeCalendarDrafts() {
        for (String authority : new String[]{"PPR_PLAN_GENERATE", "PPR_PLAN_APPROVE"}) {
            ScopeAccessService scope = mock(ScopeAccessService.class);
            when(scope.hasAuthority(authority)).thenReturn(true);

            assertThat(new PprPlanVisibilityPolicy(scope).visibleStatuses(
                    Set.of(PlanStatus.DRAFT, PlanStatus.APPROVED)))
                    .containsExactly(PlanStatus.APPROVED);
        }
    }
}
