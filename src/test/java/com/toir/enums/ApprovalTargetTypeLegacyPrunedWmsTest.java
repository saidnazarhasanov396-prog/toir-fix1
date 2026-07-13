package com.toir.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalTargetTypeLegacyPrunedWmsTest {

    @Test
    void enumCanReadLegacyWarehouseWriteoffApprovalTemplatesUntilMigrationNormalizesThem() {
        assertThat(ApprovalTargetType.valueOf("WAREHOUSE_WRITEOFF"))
                .isEqualTo(ApprovalTargetType.WAREHOUSE_WRITEOFF);
    }
}
