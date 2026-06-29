package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContractorSupplierLegalDetailsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260627_7__contractor_supplier_legal_details.sql"
    );

    @Test
    void migrationAddsLegalAndBankColumnsToContractorsAndSuppliers() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("alter table contractors");
        assertThat(sql).contains("alter table suppliers");
        assertThat(sql).contains("director_name");
        assertThat(sql).contains("bank_name");
        assertThat(sql).contains("bank_account");
        assertThat(sql).contains("mfo");
        assertThat(sql).contains("if not exists");
    }
}
