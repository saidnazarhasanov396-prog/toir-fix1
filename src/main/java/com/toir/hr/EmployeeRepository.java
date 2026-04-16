package com.toir.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByPersonnelNumber(String personnelNumber);
    boolean existsByPersonnelNumber(String personnelNumber);
    List<Employee> findAllByActiveTrue();
    List<Employee> findAllByDepartmentId(UUID departmentId);
    List<Employee> findAllByBrigadeId(UUID brigadeId);
}
