package com.toir.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialReviewDemoSeedContractTest {

    private static final Path FINANCE_DOCS_AUDIT_SEED =
            Path.of("src/main/resources/db/demo-seed/phase-4-finance-docs-audit.sql");

    @Test
    void phase4SeedContainsFinancialReviewInboxActivityHandoverAndOverrideRows() throws Exception {
        assertThat(FINANCE_DOCS_AUDIT_SEED).exists();

        String sql = Files.readString(FINANCE_DOCS_AUDIT_SEED);

        assertThat(sql)
                .contains("INSERT INTO actual_costs")
                .contains("'PENDING'")
                .contains("INSERT INTO notifications")
                .contains("'ACTUAL_COST'")
                .contains("Finance review inbox actual cost")
                .contains("INSERT INTO actual_cost_review_events")
                .contains("'REVIEW'")
                .contains("'ROUTE'")
                .contains("'SLA'")
                .contains("'HANDOVER'")
                .contains("INSERT INTO actual_cost_review_route_overrides")
                .contains("Override approval route for high-value or urgent actual cost");
    }
}
