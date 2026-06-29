package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FakturaUzIntegrationRepairMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260627_9__faktura_uz_integration_repair.sql"
    );

    @Test
    void migrationRepairsMissingFakturaTablesAndColumns() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("alter table integration_endpoints");
        assertThat(sql).contains("client_id");
        assertThat(sql).contains("client_secret");
        assertThat(sql).contains("company_inn");
        assertThat(sql).contains("create table if not exists faktura_uz_documents");
        assertThat(sql).contains("create table if not exists faktura_uz_document_contents");
        assertThat(sql).contains("create table if not exists faktura_uz_doc_type32");
        assertThat(sql).contains("create table if not exists faktura_uz_doc32_services");
        assertThat(sql).contains("create table if not exists faktura_uz_doc32_parts");
        assertThat(sql).contains("create table if not exists faktura_uz_import_history");
        assertThat(sql).contains("add column if not exists document_unique_id");
        assertThat(sql).contains("idx_faktura_uz_doc32_parts_unique_id");
        assertThat(sql).contains("fk_faktura_uz_documents_endpoint");
    }
}
