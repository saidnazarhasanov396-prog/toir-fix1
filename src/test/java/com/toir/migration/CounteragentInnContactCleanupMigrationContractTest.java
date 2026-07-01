package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CounteragentInnContactCleanupMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260701_2__counteragent_contact_and_inn_cleanup.sql"
    );

    @Test
    void migrationBackfillsInnAndContactColumnsBeforeDroppingLegacyFields() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("add column if not exists inn varchar(9)");
        assertThat(sql).contains("add column if not exists contact_name varchar(255)");
        assertThat(sql).contains("regexp_replace(coalesce(tax_number, ''), '\\d', '', 'g')");
        assertThat(sql).contains("contact_name = contact_person");
        assertThat(sql).contains("contact_phone = phone");
        assertThat(sql).contains("contact_email = email");
        assertThat(sql).contains("ck_counteragents_inn_9_digits");
        assertThat(sql).contains("uq_counteragents_inn_active");
        assertThat(sql).contains("drop column if exists tax_number");
        assertThat(sql).contains("drop column if exists base_inn");
        assertThat(sql).contains("drop column if exists contact_person");
        assertThat(sql).contains("drop column if exists phone");
        assertThat(sql).contains("drop column if exists email");
        assertThat(sql).contains("drop column if exists specialization");
    }
}
