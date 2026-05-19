package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionSeedMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260519_3__seed_granular_role_permissions.sql"
    );

    @Test
    void migrationFileExistsAndUsesJsonbAppendLogic() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();

        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).contains("create or replace function append_role_permissions");
        assertThat(sql).contains("jsonb_array_elements_text");
        assertThat(sql).contains("unnest(permissions_to_add)");
        assertThat(sql).contains("jsonb_agg(distinct permission)");
        assertThat(sql).contains("coalesce(r.permissions, '[]'::jsonb)");
        assertThat(sql).contains("drop function append_role_permissions(text, text[])");
    }

    @Test
    void migrationPreservesSystemAdminWildcardAndLegacyRead() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("append_role_permissions('SYSTEM_ADMIN', ARRAY['*'])");
        assertThat(sql).doesNotContain("permissions = '[\"*\"]'::jsonb");
        assertThat(sql).doesNotContain("permissions = '[\"read\"]'::jsonb");
        assertThat(sql).doesNotContain("permissions - 'read'");
        assertThat(sql).doesNotContain("jsonb_set");
    }

    @Test
    void migrationDoesNotDeleteOrReplaceRolePermissions() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).doesNotContain("delete from roles");
        assertThat(sql).doesNotContain("truncate");
        assertThat(sql).doesNotContain("drop table");
        assertThat(sql).doesNotContain("from role_permissions");
        assertThat(sql).doesNotContain("join role_permissions");
        assertThat(sql).doesNotContain("create table permissions");
        assertThat(sql).doesNotContain("create table role_permissions");
    }

    @Test
    void existingAppliedRoleMigrationIsNotExpandedIntoSeedLogic() throws Exception {
        Path oldMigration = Path.of(
                "src/main/resources/db/migration/V20260425_1__soft_delete_standalone_entities.sql"
        );

        String sql = Files.readString(oldMigration).toLowerCase();

        assertThat(sql).contains("alter table if exists roles");
        assertThat(sql).contains("add column if not exists is_deleted");
        assertThat(sql).doesNotContain("append_role_permissions");
        assertThat(sql).doesNotContain("ppr_plan_read");
        assertThat(sql).doesNotContain("warehouse_read");
    }
}
