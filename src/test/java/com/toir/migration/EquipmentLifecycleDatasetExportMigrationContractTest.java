package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentLifecycleDatasetExportMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260731_1__equipment_lifecycle_dataset_export.sql"
    );

    @Test
    void migrationPersistsJobsFrozenMembershipPartsAndArtifactsWithoutPayloadBlobs() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains(
                "create table equipment_lifecycle_export_jobs",
                "create table equipment_lifecycle_export_membership",
                "create table equipment_lifecycle_export_parts",
                "create table equipment_lifecycle_export_artifacts",
                "resolved_policy jsonb",
                "authorization_scope jsonb",
                "selection_frozen",
                "lease_token",
                "fencing_token",
                "sha256"
        );
        assertThat(sql).doesNotContain(" bytea", " blob", " clob", "exported_json", "dataset_payload");
    }

    @Test
    void migrationEnforcesIdempotencyAndDeterministicMembershipAndPartOrdering() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains(
                "unique (creator_id, idempotency_key)",
                "unique (job_id, equipment_id)",
                "unique (job_id, ordinal)",
                "unique (job_id, part_number)",
                "unique (job_id, artifact_type)",
                "check (ordinal >= 0)",
                "check (object_size >= 0)"
        );
    }

    @Test
    void migrationIndexesLifecycleLeaseExpiryAndOrdinalQueries() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains(
                "idx_equipment_lifecycle_export_jobs_status_created",
                "idx_equipment_lifecycle_export_jobs_expiry_status",
                "idx_equipment_lifecycle_export_jobs_lease_status",
                "idx_equipment_lifecycle_export_membership_job_ordinal"
        );
        assertThat(sql).doesNotContain("references equipment(");
    }
}
