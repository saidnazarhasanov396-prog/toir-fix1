package com.toir.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionDefaultsTest {

    private static final Path ROLE_PERMISSION_DEFAULTS = Path.of(
            "src/main/java/com/toir/security/RolePermissionDefaults.java"
    );

    @Test
    void financeAndSupplyRolesCanReadCounteragentsButOnlyManagersCanMutateThem() {
        assertThat(RolePermissionDefaults.forRole("FINANCE_MANAGER")).contains(
                PermissionConstants.COUNTERAGENT_READ,
                PermissionConstants.COUNTERAGENT_CREATE,
                PermissionConstants.COUNTERAGENT_UPDATE
        );
        assertThat(RolePermissionDefaults.forRole("SUPPLY_SPECIALIST")).contains(
                PermissionConstants.COUNTERAGENT_READ,
                PermissionConstants.COUNTERAGENT_CREATE,
                PermissionConstants.COUNTERAGENT_UPDATE
        );
        assertThat(RolePermissionDefaults.forRole("ECONOMIST")).contains(PermissionConstants.COUNTERAGENT_READ);
        assertThat(RolePermissionDefaults.forRole("VIEWER")).contains(PermissionConstants.COUNTERAGENT_READ);

        assertThat(RolePermissionDefaults.forRole("ECONOMIST")).doesNotContain(
                PermissionConstants.COUNTERAGENT_CREATE,
                PermissionConstants.COUNTERAGENT_UPDATE,
                PermissionConstants.COUNTERAGENT_DELETE
        );
        assertThat(RolePermissionDefaults.forRole("VIEWER")).doesNotContain(
                PermissionConstants.COUNTERAGENT_CREATE,
                PermissionConstants.COUNTERAGENT_UPDATE,
                PermissionConstants.COUNTERAGENT_DELETE
        );
    }

    @Test
    void roleDefaultsDoNotDefineAnyRoleMoreThanOnce() throws Exception {
        List<String> roleCodes = defaultRoleCodes();

        assertThat(roleCodes).doesNotHaveDuplicates();
    }

    @Test
    void technicianDefaultsContainMaintenanceExecutionPermissions() {
        assertThat(RolePermissionDefaults.forRole("TECHNICIAN"))
                .contains(
                        PermissionConstants.READ_LEGACY,
                        PermissionConstants.REPAIR_REQUEST_READ,
                        PermissionConstants.WORK_ORDER_READ,
                        PermissionConstants.WORK_ORDER_UPDATE,
                        PermissionConstants.WORK_ORDER_START,
                        PermissionConstants.WORK_ORDER_COMPLETE,
                        PermissionConstants.EQUIPMENT_READ,
                        PermissionConstants.DEFECT_READ,
                        PermissionConstants.PPR_TASK_READ,
                        PermissionConstants.INSPECTION_READ,
                        PermissionConstants.INSPECTION_START,
                        PermissionConstants.INSPECTION_COMPLETE,
                        PermissionConstants.TIMESHEET_CREATE,
                        PermissionConstants.KNOWLEDGE_READ,
                        PermissionConstants.NOTIFICATION_READ,
                        PermissionConstants.NOTIFICATION_MARK_READ
                );
    }

    @Test
    void technicianDefaultsKeepPreviouslyOverwrittenPermissions() {
        assertThat(RolePermissionDefaults.forRole("TECHNICIAN"))
                .contains(
                        PermissionConstants.DEFECT_READ,
                        PermissionConstants.PPR_TASK_READ,
                        PermissionConstants.INSPECTION_READ,
                        PermissionConstants.INSPECTION_START,
                        PermissionConstants.INSPECTION_COMPLETE,
                        PermissionConstants.TIMESHEET_CREATE
                );
    }

    @Test
    void technicianDefaultsDoNotGrantAdminFinanceProcurementOrWmsPermissions() {
        assertThat(RolePermissionDefaults.forRole("TECHNICIAN"))
                .doesNotContain(
                        PermissionConstants.WILDCARD,
                        PermissionConstants.USER_CREATE,
                        PermissionConstants.USER_UPDATE,
                        PermissionConstants.USER_DELETE,
                        PermissionConstants.ROLE_CREATE,
                        PermissionConstants.ROLE_UPDATE,
                        PermissionConstants.ROLE_DELETE,
                        PermissionConstants.BUDGET_APPROVE,
                        PermissionConstants.ACTUAL_COST_APPROVE,
                        PermissionConstants.FINANCE_REVIEW_ALLOCATE,
                        PermissionConstants.PROCUREMENT_CREATE,
                        PermissionConstants.PROCUREMENT_APPROVE,
                        PermissionConstants.PROCUREMENT_ORDER,
                        PermissionConstants.INVENTORY_ADJUSTMENT,
                        PermissionConstants.INVENTORY_VALUATION_READ,
                        PermissionConstants.WAREHOUSE_CREATE,
                        PermissionConstants.WAREHOUSE_UPDATE,
                        PermissionConstants.WAREHOUSE_DELETE,
                        PermissionConstants.STOCK_ADJUST
                );
    }

    private List<String> defaultRoleCodes() throws Exception {
        String source = Files.readString(ROLE_PERMISSION_DEFAULTS);
        Matcher matcher = Pattern.compile("defaults\\.put\\(\\\"([^\\\"]+)\\\"").matcher(source);
        List<String> roleCodes = new ArrayList<>();
        while (matcher.find()) {
            roleCodes.add(matcher.group(1));
        }
        return roleCodes;
    }
}
