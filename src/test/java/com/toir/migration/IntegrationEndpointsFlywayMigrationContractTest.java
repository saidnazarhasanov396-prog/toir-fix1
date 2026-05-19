package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationEndpointsFlywayMigrationContractTest {

    @Test
    void migrationMustCreateIntegrationEndpointsAndHardenSyncFlags() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V20260519_1__integration_endpoints_table_foundation.sql"
        );
        assertThat(Files.exists(migration)).isTrue();

        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("create table integration_endpoints");
        assertThat(sql).contains("if to_regclass('public.integration_endpoints') is null then");
        assertThat(sql).contains("sync_work_orders boolean");
        assertThat(sql).contains("sync_downtimes boolean");
        assertThat(sql).contains("sync_defects boolean");
        assertThat(sql).contains("sync_scada boolean");
        assertThat(sql).contains("sync_production boolean");
        assertThat(sql).contains("add column if not exists sync_work_orders boolean");
        assertThat(sql).contains("alter column sync_work_orders set not null");
    }
}
