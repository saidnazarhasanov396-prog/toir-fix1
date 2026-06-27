package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierBaseInnMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260627_5__supplier_base_inn.sql"
    );

    @Test
    void migrationAddsBaseInnColumnIndexAndBackfill() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("base_inn");
        assertThat(sql).contains("regexp_replace");
        assertThat(sql).contains("idx_suppliers_base_inn");
        assertThat(sql).contains("if not exists");
    }
}
