package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PartyBankAccountsAndInnStringMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260627_6__party_bank_accounts_and_inn_string.sql"
    );

    @Test
    void migrationCreatesBankAccountListTablesAndEnsuresInnStringColumns() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("create table if not exists contractor_bank_accounts");
        assertThat(sql).contains("create table if not exists supplier_bank_accounts");
        assertThat(sql).contains("account_order");
        assertThat(sql).doesNotContain("jsonb");
        assertThat(sql).contains("drop column if exists bank_name");
        assertThat(sql).contains("alter column tax_number type varchar(32)");
        assertThat(sql).contains("alter column base_inn type varchar(32)");
    }
}
