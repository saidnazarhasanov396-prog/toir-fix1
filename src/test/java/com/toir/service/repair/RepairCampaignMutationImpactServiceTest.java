package com.toir.service.repair;

import com.toir.dto.repaircampaign.CampaignMutationImpact;
import com.toir.enums.RepairCampaignMutationType;
import com.toir.enums.RepairCampaignStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

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
}
