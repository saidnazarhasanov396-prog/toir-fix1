package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignApprovalRouteMigrationContractTest {

    @Test
    void v6DefinesImmutableCampaignApprovalRouteAndConservativeSeeds() throws Exception {
        Path path = Path.of("src/main/resources/db/migration/V20260712_6__repair_campaign_approval_route.sql");
        assertThat(path).exists();

        String sql = Files.readString(path);
        assertThat(sql).contains(
                "scope_version",
                "REPAIR_CAMPAIGN_APPROVAL",
                "REPAIR_CAMPAIGN_CHIEF_MECHANIC_APPROVER",
                "REPAIR_CAMPAIGN_PRODUCTION_APPROVER",
                "REPAIR_CAMPAIGN_WAREHOUSE_APPROVER",
                "REPAIR_CAMPAIGN_PROCUREMENT_APPROVER",
                "REPAIR_CAMPAIGN_FINANCE_APPROVER",
                "REPAIR_CAMPAIGN_HSE_APPROVER",
                "REPAIR_CAMPAIGN_CHIEF_ENGINEER_APPROVER",
                "REPAIR_CAMPAIGN_MANAGE_WORK",
                "REPAIR_CAMPAIGN_MANAGE_SCOPE",
                "REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS",
                "REPAIR_CAMPAIGN_MANAGE_RESOURCES",
                "REPAIR_CAMPAIGN_MANAGE_MATERIALS",
                "REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES",
                "REPAIR_CAMPAIGN_MANAGE_FINANCE",
                "REPAIR_CAMPAIGN_REQUEST_APPROVAL",
                "REPAIR_CAMPAIGN_OUTBOX_READ",
                "REPAIR_CAMPAIGN_OUTBOX_RETRY");
    }

    @Test
    void routeContainsExactlySevenOrderedDisciplineSteps() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260712_6__repair_campaign_approval_route.sql"));

        for (int sequence = 1; sequence <= 7; sequence++) {
            assertThat(sql).contains("'REPAIR_CAMPAIGN_APPROVAL', " + sequence);
        }
        assertThat(sql).doesNotContain("'REPAIR_CAMPAIGN_APPROVAL', 8");
    }
    @Test
    void legacyPendingNonCanonicalRepairCampaignApprovalsAreCancelledIdempotently() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260714_1__cancel_noncanonical_repair_campaign_approvals.sql"));

        assertThat(sql).contains(
                "NONCANONICAL_REPAIR_CAMPAIGN_ROUTE",
                "UPDATE approval_requests",
                "status = 'CANCELLED'",
                "WHERE ar.status = 'PENDING'",
                "COALESCE(ar.target_type, ar.document_type) = 'REPAIR_CAMPAIGN'",
                "COALESCE(ar.action_type, 'APPROVE') = 'APPROVE'");
        assertThat(sql).contains("NOT EXISTS");
        assertThat(sql).doesNotContain("DELETE FROM approval_requests", "DELETE FROM approval_steps");
    }

}
