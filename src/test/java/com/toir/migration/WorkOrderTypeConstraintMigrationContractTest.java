package com.toir.migration;

import com.toir.enums.WorkOrderType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderTypeConstraintMigrationContractTest {

    @Test
    void migrationIncludesEveryWorkOrderTypeEnumValue() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260609_1__work_order_type_constraint_medium_capital.sql"));

        for (WorkOrderType type : WorkOrderType.values()) {
            assertThat(sql).contains("'" + type.name() + "'");
        }
    }
}
