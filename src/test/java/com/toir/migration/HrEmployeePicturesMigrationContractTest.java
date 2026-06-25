package com.toir.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HrEmployeePicturesMigrationContractTest {

    @Test
    void migrationCreatesEmployeePictureHandleTable() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260625_1__hr_employee_pictures.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS hr_employee_pictures");
        assertThat(sql).contains("employee_id UUID NOT NULL REFERENCES hr_employees(id)");
        assertThat(sql).contains("file_id UUID NOT NULL REFERENCES uploaded_files(id)");
        assertThat(sql).contains("picture_type VARCHAR(64)");
        assertThat(sql).contains("picture_name VARCHAR(255)");
        assertThat(sql).contains("idx_hr_employee_pictures_employee_id");
        assertThat(sql).contains("idx_hr_employee_pictures_file_id");
        assertThat(sql).contains("idx_hr_employee_pictures_employee_picture_type");
    }
}
