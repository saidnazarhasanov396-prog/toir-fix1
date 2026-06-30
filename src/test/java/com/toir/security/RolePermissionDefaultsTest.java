package com.toir.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionDefaultsTest {

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
}
