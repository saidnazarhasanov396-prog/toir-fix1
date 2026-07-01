package com.toir.repository.users;

import com.toir.test.RepositorySliceTest;
import com.toir.entity.users.Employee;
import com.toir.repository.projects.EmployeeStatsProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
class EmployeeRepositoryStatsTest {

    @Autowired
    EmployeeRepository repository;

    @Test
    void getEmployeeStatsWithoutFiltersCountsEmployees() {
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        saveEmployee("EMP-001", "Ali", "Valiyev", departmentId, brigadeId, true, null, "ali@example.com");
        saveEmployee("EMP-002", "Vali", "Aliyev", departmentId, brigadeId, true, null, "");

        saveEmployee("EMP-003", "Hasan", "Karimov", departmentId, brigadeId, false, LocalDate.of(2026, 1, 1), "hasan@example.com");

        EmployeeStatsProjection stats = repository.getEmployeeStats(null, null, null);

        assertThat(stats.getTotal()).isEqualTo(3);
        assertThat(stats.getActive()).isEqualTo(2);
        assertThat(stats.getTerminated()).isEqualTo(1);
        assertThat(stats.getWithoutEmail()).isEqualTo(1);
    }

    @Test
    void getEmployeeStatsWithDepartmentBrigadeAndSearchCountsOnlyMatchingEmployees() {
        UUID targetDepartmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        UUID targetBrigadeId = UUID.randomUUID();
        UUID otherBrigadeId = UUID.randomUUID();

        saveEmployee("EMP-ALI-001", "Ali", "Valiyev", targetDepartmentId, targetBrigadeId, true, null, "ali@example.com");
        saveEmployee("EMP-ALI-002", "Ali", "Karimov", targetDepartmentId, targetBrigadeId, false, LocalDate.of(2026, 1, 1), null);

        saveEmployee("EMP-ALI-003", "Ali", "OtherDept", otherDepartmentId, targetBrigadeId, true, null, "x@example.com");
        saveEmployee("EMP-ALI-004", "Ali", "OtherBrigade", targetDepartmentId, otherBrigadeId, true, null, "x@example.com");
        saveEmployee(
                "EMP-BEK-001",
                "Bekzod",
                "NoMatch",
                targetDepartmentId,
                otherBrigadeId,
                true,
                null,
                "x@example.com"
        );
        EmployeeStatsProjection stats = repository.getEmployeeStats(
                targetDepartmentId,
                targetBrigadeId,
                "%ali%"
        );

        assertThat(stats.getTotal()).isEqualTo(2);
        assertThat(stats.getActive()).isEqualTo(1);
        assertThat(stats.getTerminated()).isEqualTo(1);
        assertThat(stats.getWithoutEmail()).isEqualTo(1);
    }

    private void saveEmployee(
            String personnelNumber,
            String firstName,
            String lastName,
            UUID departmentId,
            UUID brigadeId,
            boolean active,
            LocalDate terminatedDate,
            String email
    ) {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setPersonnelNumber(personnelNumber);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setPosition("Engineer");
        employee.setDepartmentId(departmentId);
        employee.setBrigadeId(brigadeId);
        employee.setHireDate(LocalDate.of(2025, 1, 10));
        employee.setActive(active);
        employee.setTerminatedDate(terminatedDate);
        employee.setEmail(email);
        employee.setDeleted(false);

        repository.save(employee);
    }
}