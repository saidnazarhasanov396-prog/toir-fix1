package com.toir.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionDefaultsTest {

    @Test
    void financeAndSupplyRolesCanReadContractorsButOnlyManagersCanMutateThem() {
        assertThat(RolePermissionDefaults.forRole("FINANCE_MANAGER")).contains(
                PermissionConstants.CONTRACTOR_READ,
                PermissionConstants.CONTRACTOR_CREATE,
                PermissionConstants.CONTRACTOR_UPDATE
        );
        assertThat(RolePermissionDefaults.forRole("SUPPLY_SPECIALIST")).contains(
                PermissionConstants.CONTRACTOR_READ,
                PermissionConstants.CONTRACTOR_CREATE,
                PermissionConstants.CONTRACTOR_UPDATE
        );
        assertThat(RolePermissionDefaults.forRole("ECONOMIST")).contains(PermissionConstants.CONTRACTOR_READ);
        assertThat(RolePermissionDefaults.forRole("VIEWER")).contains(PermissionConstants.CONTRACTOR_READ);

        assertThat(RolePermissionDefaults.forRole("ECONOMIST")).doesNotContain(
                PermissionConstants.CONTRACTOR_CREATE,
                PermissionConstants.CONTRACTOR_UPDATE,
                PermissionConstants.CONTRACTOR_DELETE
        );
        assertThat(RolePermissionDefaults.forRole("VIEWER")).doesNotContain(
                PermissionConstants.CONTRACTOR_CREATE,
                PermissionConstants.CONTRACTOR_UPDATE,
                PermissionConstants.CONTRACTOR_DELETE
        );
    }
}
