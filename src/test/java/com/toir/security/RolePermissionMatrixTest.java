package com.toir.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionMatrixTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260519_3__seed_granular_role_permissions.sql"
    );

    @Test
    void migrationSeedsConservativePermissionsForBuiltInRoles() throws Exception {
        Map<String, Set<String>> matrix = migrationMatrix();

        assertThat(matrix.get("PPR_ENGINEER")).contains(
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_PLAN_CREATE,
                PermissionConstants.PPR_PLAN_UPDATE,
                PermissionConstants.PPR_PLAN_GENERATE,
                PermissionConstants.PPR_TASK_READ,
                PermissionConstants.PPR_TASK_CREATE,
                PermissionConstants.PPR_TASK_START,
                PermissionConstants.PPR_TASK_COMPLETE,
                PermissionConstants.PPR_TASK_POSTPONE,
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.WORK_ORDER_READ,
                PermissionConstants.REPAIR_REQUEST_READ,
                PermissionConstants.KNOWLEDGE_READ
        );
        assertThat(matrix.get("PPR_ENGINEER")).doesNotContain(
                PermissionConstants.PPR_PLAN_APPROVE,
                PermissionConstants.PPR_TASK_APPROVE
        );

        assertThat(matrix.get("RELIABILITY_ENGINEER")).contains(
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.EQUIPMENT_UPDATE,
                PermissionConstants.DEFECT_READ,
                PermissionConstants.DEFECT_CREATE,
                PermissionConstants.DEFECT_UPDATE,
                PermissionConstants.DEFECT_RESOLVE,
                PermissionConstants.DEFECT_LIST_READ,
                PermissionConstants.KNOWLEDGE_READ,
                PermissionConstants.KNOWLEDGE_CREATE,
                PermissionConstants.KNOWLEDGE_UPDATE,
                PermissionConstants.ANALYTICS_READ,
                PermissionConstants.ANALYTICS_EXPORT,
                PermissionConstants.WORK_ORDER_READ,
                PermissionConstants.REPAIR_REQUEST_READ
        );

        assertThat(matrix.get("FOREMAN")).contains(
                PermissionConstants.REPAIR_REQUEST_READ,
                PermissionConstants.REPAIR_REQUEST_UPDATE,
                PermissionConstants.REPAIR_REQUEST_APPROVE,
                PermissionConstants.REPAIR_REQUEST_ASSIGN,
                PermissionConstants.REPAIR_REQUEST_REJECT,
                PermissionConstants.REPAIR_REQUEST_CLOSE,
                PermissionConstants.WORK_ORDER_READ,
                PermissionConstants.WORK_ORDER_CREATE,
                PermissionConstants.WORK_ORDER_APPROVE,
                PermissionConstants.WORK_ORDER_START,
                PermissionConstants.WORK_ORDER_COMPLETE,
                PermissionConstants.WORK_ORDER_CLOSE,
                PermissionConstants.DEFECT_READ,
                PermissionConstants.DEFECT_CREATE,
                PermissionConstants.DEFECT_UPDATE,
                PermissionConstants.DEFECT_RESOLVE,
                PermissionConstants.DEFECT_LIST_READ,
                PermissionConstants.DEFECT_LIST_CREATE,
                PermissionConstants.DEFECT_LIST_UPDATE,
                PermissionConstants.DEFECT_LIST_APPROVE,
                PermissionConstants.DEFECT_LIST_CLOSE,
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.EMPLOYEE_READ,
                PermissionConstants.TIMESHEET_READ,
                PermissionConstants.TIMESHEET_APPROVE,
                PermissionConstants.KNOWLEDGE_READ,
                PermissionConstants.ANALYTICS_READ
        );

        assertThat(matrix.get("TECHNICAL_DIRECTOR")).contains(
                PermissionConstants.EQUIPMENT_CREATE,
                PermissionConstants.PPR_PLAN_APPROVE,
                PermissionConstants.PPR_TASK_APPROVE,
                PermissionConstants.INSPECTION_COMPLETE,
                PermissionConstants.ANALYTICS_EXPORT
        );

        assertThat(matrix.get("STOREKEEPER")).contains(
                PermissionConstants.WAREHOUSE_READ,
                PermissionConstants.WAREHOUSE_EQUIPMENT_READ,
                PermissionConstants.WAREHOUSE_EQUIPMENT_STATUS_UPDATE,
                PermissionConstants.STOCK_READ,
                PermissionConstants.STOCK_RECEIVE,
                PermissionConstants.STOCK_ISSUE,
                PermissionConstants.STOCK_MOVE,
                PermissionConstants.MATERIAL_USAGE_READ,
                PermissionConstants.MATERIAL_USAGE_ISSUE,
                PermissionConstants.SPARE_PART_READ,
                PermissionConstants.SPARE_PART_CREATE,
                PermissionConstants.SPARE_PART_UPDATE,
                PermissionConstants.WORK_ORDER_READ,
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.PROCUREMENT_READ
        );

        assertThat(matrix.get("SUPPLY_SPECIALIST")).contains(
                PermissionConstants.PROCUREMENT_READ,
                PermissionConstants.PROCUREMENT_CREATE,
                PermissionConstants.PROCUREMENT_SUBMIT,
                PermissionConstants.PROCUREMENT_ORDER,
                PermissionConstants.PROCUREMENT_RECEIVE,
                PermissionConstants.PROCUREMENT_CANCEL,
                PermissionConstants.WAREHOUSE_READ,
                PermissionConstants.STOCK_READ,
                PermissionConstants.SPARE_PART_READ,
                PermissionConstants.SPARE_PART_CREATE,
                PermissionConstants.SPARE_PART_UPDATE,
                PermissionConstants.ANALYTICS_READ
        );

        assertThat(matrix.get("ECONOMIST")).contains(
                PermissionConstants.ACTUAL_COST_READ,
                PermissionConstants.ACTUAL_COST_CREATE,
                PermissionConstants.BUDGET_READ,
                PermissionConstants.BUDGET_CREATE,
                PermissionConstants.BUDGET_UPDATE,
                PermissionConstants.ANALYTICS_READ,
                PermissionConstants.ANALYTICS_EXPORT,
                PermissionConstants.APPROVAL_READ,
                PermissionConstants.APPROVAL_CREATE
        );
        assertThat(matrix.get("ECONOMIST")).doesNotContain(
                PermissionConstants.ACTUAL_COST_APPROVE,
                PermissionConstants.ACTUAL_COST_REJECT,
                PermissionConstants.BUDGET_APPROVE
        );

        assertThat(matrix.get("VIEWER")).contains(
                PermissionConstants.EQUIPMENT_READ,
                PermissionConstants.REPAIR_REQUEST_READ,
                PermissionConstants.WORK_ORDER_READ,
                PermissionConstants.DEFECT_READ,
                PermissionConstants.DEFECT_LIST_READ,
                PermissionConstants.PPR_PLAN_READ,
                PermissionConstants.PPR_TASK_READ,
                PermissionConstants.WAREHOUSE_READ,
                PermissionConstants.STOCK_READ,
                PermissionConstants.SPARE_PART_READ,
                PermissionConstants.PROCUREMENT_READ,
                PermissionConstants.BUDGET_READ,
                PermissionConstants.ACTUAL_COST_READ,
                PermissionConstants.APPROVAL_READ,
                PermissionConstants.INSPECTION_READ,
                PermissionConstants.KNOWLEDGE_READ,
                PermissionConstants.ANALYTICS_READ,
                PermissionConstants.DEPARTMENT_READ,
                PermissionConstants.BRIGADE_READ,
                PermissionConstants.LOCATION_READ,
                PermissionConstants.EQUIPMENT_TYPE_READ,
                PermissionConstants.CATEGORY_READ
        );
    }

    @Test
    void migrationDoesNotGrantDangerousPermissionsByDefault() throws Exception {
        Map<String, Set<String>> matrix = migrationMatrix();

        assertThat(matrix.get("SYSTEM_ADMIN")).containsExactly(PermissionConstants.WILDCARD);
        assertThat(matrix).doesNotContainKey("CONTRACTOR");
        assertThat(matrix.get("PPR_ENGINEER")).doesNotContain(
                PermissionConstants.PPR_PLAN_APPROVE,
                PermissionConstants.PPR_TASK_APPROVE,
                PermissionConstants.PPR_PLAN_DELETE,
                PermissionConstants.PPR_TASK_CANCEL
        );
        assertThat(matrix.get("STOREKEEPER")).doesNotContain(
                PermissionConstants.STOCK_ADJUST,
                PermissionConstants.WAREHOUSE_DELETE,
                PermissionConstants.SPARE_PART_DELETE
        );
        assertThat(matrix.get("SUPPLY_SPECIALIST")).doesNotContain(
                PermissionConstants.PROCUREMENT_APPROVE,
                PermissionConstants.PROCUREMENT_REJECT
        );
        assertThat(matrix.get("VIEWER")).noneMatch(permission ->
                permission.endsWith("_CREATE")
                        || permission.endsWith("_UPDATE")
                        || permission.endsWith("_DELETE")
                        || permission.endsWith("_APPROVE")
                        || permission.endsWith("_REJECT")
                        || permission.endsWith("_CLOSE")
                        || permission.endsWith("_START")
                        || permission.endsWith("_COMPLETE")
                        || permission.endsWith("_ISSUE")
                        || permission.endsWith("_MOVE")
                        || permission.endsWith("_RECEIVE")
                        || permission.endsWith("_SUBMIT")
                        || permission.endsWith("_ORDER")
                        || permission.endsWith("_CANCEL")
        );
    }

    @Test
    void optionalRolesAreUpdatedOnlyIfRowsAlreadyExist() throws Exception {
        Map<String, Set<String>> matrix = migrationMatrix();
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(matrix.get("FINANCE_MANAGER")).contains(
                PermissionConstants.ACTUAL_COST_APPROVE,
                PermissionConstants.BUDGET_APPROVE,
                PermissionConstants.FINANCE_ROUTE_OVERRIDE_APPLY,
                PermissionConstants.APPROVAL_APPROVE
        );
        assertThat(matrix.get("INSPECTOR")).contains(
                PermissionConstants.INSPECTION_READ,
                PermissionConstants.INSPECTION_CREATE,
                PermissionConstants.INSPECTION_START,
                PermissionConstants.INSPECTION_COMPLETE
        );
        assertThat(matrix.get("HR_MANAGER")).contains(
                PermissionConstants.EMPLOYEE_CREATE,
                PermissionConstants.TIMESHEET_APPROVE,
                PermissionConstants.BRIGADE_UPDATE,
                PermissionConstants.DEPARTMENT_READ
        );

        assertThat(sql).doesNotContain("insert into roles");
        assertThat(sql).doesNotContain("on conflict");
    }

    private Map<String, Set<String>> migrationMatrix() throws Exception {
        String sql = Files.readString(MIGRATION);
        Pattern callPattern = Pattern.compile(
                "append_role_permissions\\('([^']+)',\\s*ARRAY\\[(.*?)\\]\\)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = callPattern.matcher(sql);
        java.util.LinkedHashMap<String, Set<String>> matrix = new java.util.LinkedHashMap<>();
        while (matcher.find()) {
            String roleCode = matcher.group(1);
            Set<String> permissions = new LinkedHashSet<>();
            Arrays.stream(matcher.group(2).split(","))
                    .map(String::trim)
                    .map(value -> value.replace("'", ""))
                    .filter(value -> !value.isBlank())
                    .forEach(permissions::add);
            matrix.put(roleCode, permissions);
        }
        return matrix;
    }
}
