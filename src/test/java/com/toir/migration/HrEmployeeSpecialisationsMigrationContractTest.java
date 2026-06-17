package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HrEmployeeSpecialisationsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260617_2__hr_employee_specialisations.sql");

    @Test
    void migrationCreatesSpecialisationDictionaryAndEmployeeRelation() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS hr_employee_specialisations");
        assertThat(sql).contains("name_ru varchar(255) NOT NULL");
        assertThat(sql).contains("name_en varchar(255) NOT NULL");
        assertThat(sql).contains("name_uz varchar(255) NOT NULL");
        assertThat(sql).contains("is_active boolean NOT NULL DEFAULT true");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS specialisation_id uuid");
        assertThat(sql).contains("idx_hr_employees_specialisation");
        assertThat(sql).contains("fk_hr_employees_specialisation");
        assertThat(sql).contains("FOREIGN KEY (specialisation_id) REFERENCES hr_employee_specialisations(id)");
    }
}
