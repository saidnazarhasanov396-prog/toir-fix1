package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentResponsibleEmployeeIdentityMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260711_3__equipment_responsible_employee_identity.sql");

    @Test
    void migrationSafelyEnforcesEmployeeIdentity() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("REFERENCES hr_employees(id) NOT VALID")
                .contains("VALIDATE CONSTRAINT fk_equipment_responsible_employee")
                .contains("CREATE INDEX IF NOT EXISTS idx_equipment_responsible_id")
                .doesNotContain("SET responsible_id = NULL")
                .doesNotContain("DELETE FROM equipment");
    }
}
