package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyStockWriteGuardMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260619_3__guard_legacy_stock_writes.sql"
    );

    @Test
    void guardsLegacyQuantitiesBehindProjectionSessionFlag() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("toir.legacy_stock_projection")
                .contains("NEW.quantity IS DISTINCT FROM OLD.quantity")
                .contains("NEW.reserved_qty IS DISTINCT FROM OLD.reserved_qty")
                .contains("read-only WMS projection")
                .contains("BEFORE INSERT OR UPDATE OR DELETE");
    }

    @Test
    void makesLegacyMovementHistoryAppendOnly() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("guard_legacy_stock_movement_mutation")
                .contains("append-only compatibility history")
                .contains("BEFORE UPDATE OR DELETE")
                .contains("warehouse_stock_ledgers");
    }

    @Test
    void demoSeedsExplicitlyScopeCompatibilityProjectionWrites() throws Exception {
        String phase2 = Files.readString(Path.of(
                "src/main/resources/db/demo-seed/phase-2-equipment-warehouse.sql"));
        String phase5 = Files.readString(Path.of(
                "src/main/resources/db/demo-seed/phase-5-p0-demo.sql"));

        assertThat(phase2)
                .contains("set_config('toir.legacy_stock_projection', 'on', false)")
                .contains("set_config('toir.legacy_stock_projection', 'off', false)");
        assertThat(phase5)
                .contains("set_config('toir.legacy_stock_projection', 'on', false)")
                .contains("set_config('toir.legacy_stock_projection', 'off', false)");
    }
}
