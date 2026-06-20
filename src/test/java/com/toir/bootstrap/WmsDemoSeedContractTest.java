package com.toir.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WmsDemoSeedContractTest {

    @Test
    void sampleDataSeederPostsOpeningStockToWmsCore() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/toir/config/SampleDataSeeder.java"
        ));

        assertThat(source)
                .contains("ToirStockService")
                .contains("StockLedgerMovementType.ADJUSTMENT_INC")
                .contains("sample-data-opening-stock:");
    }

    @Test
    void sqlStockSeedsPopulateWmsBalancesAndOpeningLedgers() throws Exception {
        String phase2 = Files.readString(Path.of(
                "src/main/resources/db/demo-seed/phase-2-equipment-warehouse.sql"
        ));
        String phase5 = Files.readString(Path.of(
                "src/main/resources/db/demo-seed/phase-5-p0-demo.sql"
        ));

        assertThat(phase2)
                .contains("INSERT INTO warehouse_stock_balances")
                .contains("INSERT INTO warehouse_stock_ledgers")
                .contains("INSERT INTO warehouse_reservation_ledgers")
                .contains("demo-seed-opening-stock:");
        assertThat(phase5)
                .contains("INSERT INTO warehouse_stock_balances")
                .contains("INSERT INTO warehouse_stock_ledgers")
                .contains("INSERT INTO warehouse_reservation_ledgers")
                .contains("demo-seed-opening-stock:");
    }
}
