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
        assertThat(sql).doesNotContain("append_role_permissions('CONTRACTOR'");
        assertThat(sql).doesNotContain("permissions = '[\"*\"]'::jsonb");
        assertThat(sql).doesNotContain("permissions = '[\"read\"]'::jsonb");
        assertThat(sql).doesNotContain("permissions - 'read'");
        assertThat(sql).doesNotContain("'read' - permissions");
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
    void migrationKeepsConservativeNegativeGrantsOutOfSeedMatrix() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(roleBlock(sql, "PPR_ENGINEER")).doesNotContain(
                "PPR_PLAN_APPROVE",
                "PPR_TASK_APPROVE",
                "PPR_PLAN_DELETE",
                "PPR_TASK_CANCEL"
        );
        assertThat(roleBlock(sql, "STOREKEEPER")).doesNotContain(
                "STOCK_ADJUST",
                "WAREHOUSE_DELETE",
                "SPARE_PART_DELETE"
        );
        assertThat(roleBlock(sql, "SUPPLY_SPECIALIST")).doesNotContain(
                "PROCUREMENT_APPROVE",
                "PROCUREMENT_REJECT"
        );
        assertThat(roleBlock(sql, "ECONOMIST")).doesNotContain(
                "ACTUAL_COST_APPROVE",
                "ACTUAL_COST_REJECT"
        );
        assertThat(roleBlock(sql, "VIEWER")).doesNotContain(
                "EQUIPMENT_CREATE",
                "EQUIPMENT_UPDATE",
                "EQUIPMENT_DELETE",
                "EQUIPMENT_TRANSFER",
                "REPAIR_REQUEST_CREATE",
                "REPAIR_REQUEST_UPDATE",
                "REPAIR_REQUEST_APPROVE",
                "REPAIR_REQUEST_ASSIGN",
                "REPAIR_REQUEST_REJECT",
                "REPAIR_REQUEST_CLOSE",
                "WORK_ORDER_CREATE",
                "WORK_ORDER_APPROVE",
                "WORK_ORDER_START",
                "WORK_ORDER_COMPLETE",
                "WORK_ORDER_CLOSE",
                "DEFECT_CREATE",
                "DEFECT_UPDATE",
                "DEFECT_RESOLVE",
                "DEFECT_LIST_CREATE",
                "DEFECT_LIST_UPDATE",
                "DEFECT_LIST_APPROVE",
                "DEFECT_LIST_CLOSE",
                "PPR_PLAN_CREATE",
                "PPR_PLAN_UPDATE",
                "PPR_PLAN_APPROVE",
                "PPR_PLAN_GENERATE",
                "PPR_PLAN_DELETE",
                "PPR_TASK_CREATE",
                "PPR_TASK_APPROVE",
                "PPR_TASK_START",
                "PPR_TASK_COMPLETE",
                "PPR_TASK_POSTPONE",
                "PPR_TASK_CANCEL",
                "WAREHOUSE_CREATE",
                "WAREHOUSE_UPDATE",
                "WAREHOUSE_DELETE",
                "STOCK_RECEIVE",
                "STOCK_ISSUE",
                "STOCK_MOVE",
                "STOCK_ADJUST",
                "MATERIAL_USAGE_ISSUE",
                "SPARE_PART_CREATE",
                "SPARE_PART_UPDATE",
                "SPARE_PART_DELETE",
                "PROCUREMENT_CREATE",
                "PROCUREMENT_SUBMIT",
                "PROCUREMENT_APPROVE",
                "PROCUREMENT_REJECT",
                "PROCUREMENT_ORDER",
                "PROCUREMENT_RECEIVE",
                "PROCUREMENT_CANCEL",
                "ACTUAL_COST_CREATE",
                "ACTUAL_COST_APPROVE",
                "ACTUAL_COST_REJECT",
                "BUDGET_CREATE",
                "BUDGET_UPDATE",
                "BUDGET_APPROVE",
                "APPROVAL_CREATE",
                "APPROVAL_APPROVE",
                "APPROVAL_REJECT",
                "INSPECTION_CREATE",
                "INSPECTION_UPDATE",
                "INSPECTION_START",
                "INSPECTION_COMPLETE",
                "KNOWLEDGE_CREATE",
                "KNOWLEDGE_UPDATE",
                "KNOWLEDGE_DELETE",
                "EMPLOYEE_CREATE",
                "EMPLOYEE_UPDATE",
                "EMPLOYEE_DELETE",
                "TIMESHEET_CREATE",
                "TIMESHEET_UPDATE",
                "TIMESHEET_APPROVE",
                "TIMESHEET_DELETE",
                "BRIGADE_CREATE",
                "BRIGADE_UPDATE",
                "BRIGADE_DELETE",
                "DEPARTMENT_CREATE",
                "DEPARTMENT_UPDATE",
                "DEPARTMENT_DELETE",
                "LOCATION_CREATE",
                "LOCATION_UPDATE",
                "LOCATION_DELETE",
                "EQUIPMENT_TYPE_CREATE",
                "EQUIPMENT_TYPE_UPDATE",
                "EQUIPMENT_TYPE_DELETE",
                "CATEGORY_CREATE",
                "CATEGORY_UPDATE",
                "CATEGORY_DELETE"
        );
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

    private String roleBlock(String sql, String roleCode) {
        String start = "append_role_permissions('" + roleCode + "'";
        int startIndex = sql.indexOf(start);
        assertThat(startIndex).isNotNegative();
        int endIndex = sql.indexOf("]);", startIndex);
        assertThat(endIndex).isNotNegative();
        return sql.substring(startIndex, endIndex);
    }
}
