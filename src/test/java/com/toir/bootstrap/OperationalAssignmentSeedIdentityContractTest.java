package com.toir.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalAssignmentSeedIdentityContractTest {

    private static final Path SAMPLE_DATA_SEEDER =
            Path.of("src/main/java/com/toir/config/SampleDataSeeder.java");
    private static final Path EMPLOYEE_REPOSITORY =
            Path.of("src/main/java/com/toir/repository/users/EmployeeRepository.java");
    private static final Path NAVOIY_ASSET_SEED =
            Path.of("src/main/resources/db/navoiy-azot-seed/04_assets_equipment_vehicles.sql");

    @Test
    void javaSeedResolvesExactlyOneEmployeeAndNeverStoresTheUserId() throws Exception {
        String seeder = Files.readString(SAMPLE_DATA_SEEDER);
        String repository = Files.readString(EMPLOYEE_REPOSITORY);

        assertThat(repository)
                .contains("List<Employee> findAllByUserIdAndIsDeletedFalse(@Param(\"userId\") UUID userId)")
                .contains("ORDER BY id");
        assertThat(seeder)
                .contains("uniqueEmployeeIdForUser")
                .doesNotContain("eq.setResponsibleId(admin)");
    }

    @Test
    void navoiyEquipmentSeedProjectsOnlyAnExactEmployeeMatch() throws Exception {
        String sql = Files.readString(NAVOIY_ASSET_SEED);

        assertThat(sql)
                .contains("responsible_employee.id")
                .contains("LEFT JOIN LATERAL")
                .contains("HAVING count(*) = 1")
                .doesNotContain("d.id, u.id");
    }

    @Test
    void navoiyVehicleSeedPreservesEmployeeDriverIdentity() throws Exception {
        String sql = Files.readString(NAVOIY_ASSET_SEED);

        assertThat(sql)
                .contains("v.fuel_tank_capacity, v.carrying_capacity, v.seat_count, driver.id")
                .contains("LEFT JOIN hr_employees driver");
    }
}
