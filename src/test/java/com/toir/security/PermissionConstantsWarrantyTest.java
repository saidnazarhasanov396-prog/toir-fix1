package com.toir.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionConstantsWarrantyTest {

    @Test
    void warrantyPermissionsAreDefined() {
        assertThat(PermissionConstants.REPAIR_REQUEST_WARRANTY_DECISION)
                .isEqualTo("REPAIR_REQUEST_WARRANTY_DECISION");
        assertThat(PermissionConstants.REPAIR_REQUEST_WARRANTY_OVERRIDE)
                .isEqualTo("REPAIR_REQUEST_WARRANTY_OVERRIDE");
    }
}
