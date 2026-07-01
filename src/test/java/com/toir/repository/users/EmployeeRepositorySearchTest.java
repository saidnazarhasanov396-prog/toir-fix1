package com.toir.repository.users;

import com.toir.test.RepositorySliceTest;
import com.toir.entity.users.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
class EmployeeRepositorySearchTest {

    @Autowired
    EmployeeRepository repository;

    @Test
    void searchEmployeesWithNullPart1AndPart2ExecutesSuccessfully() {
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        saveEmployee("EMP-001", "Ali", "Valiyev", departmentId, brigadeId, true, null, "ali@example.com");
        saveEmployee("EMP-002", "Vali", "Aliyev", departmentId, brigadeId, true, null, "");

        Page<Employee> result = repository.searchEmployees(
                null,
                null, // part1 null
                null, // part2 null
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void searchEmployeesWithPart1AndPart2FindsCorrectEmployees() {
        UUID departmentId = UUID.randomUUID();
        UUID brigadeId = UUID.randomUUID();

        saveEmployee("EMP-001", "Ali", "Valiyev", departmentId, brigadeId, true, null, "ali@example.com");
        saveEmployee("EMP-002", "Vali", "Karimov", departmentId, brigadeId, true, null, "");

        Page<Employee> result = repository.searchEmployees(
                "ali valiyev", // search is not null, so it checks parts
                "ali",
                "valiyev",
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getPersonnelNumber()).isEqualTo("EMP-001");
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
