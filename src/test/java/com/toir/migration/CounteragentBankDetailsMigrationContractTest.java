package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CounteragentBankDetailsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260730_4__counteragent_bank_details.sql"
    );

    @Test
    void migrationCreatesOrderedBankDetailsWithDatabaseInvariantGuards() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("create table if not exists counteragent_bank_details");
        assertThat(sql).contains("references counteragents(id) on delete cascade");
        assertThat(sql).contains("display_order integer not null");
        assertThat(sql).contains("where is_primary = true");
        assertThat(sql).contains("unique");
        assertThat(sql).contains("display_order >= 0");
    }

    @Test
    void migrationBackfillsCompleteAndPartialLegacyTripletsWithoutCreatingEmptyRows() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("insert into counteragent_bank_details");
        assertThat(sql).contains("coalesce(nullif(btrim(c.bank_name), ''), '')");
        assertThat(sql).contains("coalesce(nullif(btrim(c.bank_account), ''), '')");
        assertThat(sql).contains("coalesce(nullif(btrim(c.mfo), ''), '')");
        assertThat(sql).contains("not exists");
        assertThat(sql).contains("nullif(btrim(c.bank_name), '') is not null");
        assertThat(sql).contains("nullif(btrim(c.bank_account), '') is not null");
        assertThat(sql).contains("nullif(btrim(c.mfo), '') is not null");
    }
}
