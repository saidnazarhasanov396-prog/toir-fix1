package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WmsHistoricalLedgerBackfillMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260619_2__wms_historical_ledger_backfill.sql"
    );

    @Test
    void backfillsPhysicalAndReservationHistoryWithDeterministicKeys() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("INSERT INTO warehouse_stock_ledgers")
                .contains("INSERT INTO warehouse_reservation_ledgers")
                .contains("legacy-stock-movement:")
                .contains("legacy-reservation-movement:")
                .contains("ON CONFLICT (idempotency_key)");
    }

    @Test
    void usesOriginalWmsMigrationTimestampAndIncludesKnownWriteGaps() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("version = '20260613.14'")
                .contains("sm.source_type = 'PURCHASE_ORDER'")
                .contains("FROM repair_material_usages usage")
                .contains("usage.stock_movement_id = sm.id");
    }

    @Test
    void createsExplicitOpeningReconciliationEntries() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("wms-backfill-opening-stock:")
                .contains("wms-backfill-opening-reservation:")
                .contains("WMS_BACKFILL_OPENING")
                .contains("SUM(qty_on_hand)")
                .contains("SUM(qty_reserved)")
                .contains("SUM(quantity)");
    }

    @Test
    void ensuresMissingWmsBalancesExistBeforeLedgerBackfill() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("INSERT INTO warehouse_stock_balances")
                .contains("FROM warehouse_stocks ws")
                .contains("NOT EXISTS")
                .contains("qty_on_hand")
                .contains("qty_reserved");
    }
}
