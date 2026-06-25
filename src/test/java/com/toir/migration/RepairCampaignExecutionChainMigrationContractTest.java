package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignExecutionChainMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260625_6__repair_campaign_execution_chain.sql"
    );
    private static final String LAST_KNOWN_DEPLOYED_MIGRATION_VERSION = "20260625_5";

    @Test
    void migrationAddsCampaignExecutionChainColumnsAndConstraints() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("scope_type");
        assertThat(sql).contains("equipment_type_id");
        assertThat(sql).contains("repair_campaign_departments");
        assertThat(sql).contains("repair_campaign_id");
        assertThat(sql).contains("repair_campaign_stage_id");
        assertThat(sql).contains("fk_work_orders_repair_campaign");
        assertThat(sql).contains("fk_work_orders_repair_campaign_stage");
        assertThat(sql).contains("idx_work_orders_repair_campaign_id");
        assertThat(sql).contains("idx_work_orders_repair_campaign_stage_id");
        assertThat(sql).contains("equipment_type");
        assertThat(sql).contains("cross_department");
        assertThat(sql).contains("owner");
        assertThat(sql).contains("executor");
        assertThat(sql).contains("participant");
        assertThat(sql).contains("approver");
    }

    @Test
    void migrationRunsAfterAlreadyDeployedJune25Migrations() {
        String version = migrationVersion(MIGRATION);

        assertThat(compareVersions(version, LAST_KNOWN_DEPLOYED_MIGRATION_VERSION))
                .isGreaterThan(0);
    }

    private String migrationVersion(Path migration) {
        String fileName = migration.getFileName().toString();
        assertThat(fileName).startsWith("V").contains("__");
        return fileName.substring(1, fileName.indexOf("__"));
    }

    private int compareVersions(String left, String right) {
        int[] leftParts = versionParts(left);
        int[] rightParts = versionParts(right);
        int maxLength = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < maxLength; i++) {
            int leftPart = i < leftParts.length ? leftParts[i] : 0;
            int rightPart = i < rightParts.length ? rightParts[i] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private int[] versionParts(String version) {
        return Arrays.stream(version.split("[._]"))
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
