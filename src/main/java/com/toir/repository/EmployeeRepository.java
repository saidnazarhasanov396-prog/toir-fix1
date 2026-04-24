package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    @Query(value = "SELECT * FROM hr_employees WHERE personnel_number = :personnelNumber AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Employee> findByPersonnelNumber(@Param("personnelNumber") String personnelNumber);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM hr_employees WHERE personnel_number = :personnelNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByPersonnelNumber(@Param("personnelNumber") String personnelNumber);

    @Query(value = "SELECT * FROM hr_employees WHERE is_active = true AND is_deleted = false", nativeQuery = true)
    List<Employee> findAllByActiveTrue();

    @Query(value = "SELECT * FROM hr_employees WHERE department_id = :departmentId AND is_deleted = false", nativeQuery = true)
    List<Employee> findAllByDepartmentId(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM hr_employees WHERE brigade_id = :brigadeId AND is_deleted = false", nativeQuery = true)
    List<Employee> findAllByBrigadeId(@Param("brigadeId") UUID brigadeId);

    @Query("select e from Employee e where " +
            "(:activeOnly is null or " +
            "(:activeOnly = true and e.isDeleted = false) or " +
            "(:activeOnly = false and e.isDeleted = true)) " +
            "and (:search is null or " +
            "lower(e.personnelNumber) like lower(concat('%', :search, '%')) or " +
            "lower(e.firstName) like lower(concat('%', :search, '%')) or " +
            "lower(e.lastName) like lower(concat('%', :search, '%')) or " +
            "lower(e.middleName) like lower(concat('%', :search, '%')) or " +
            "lower(e.position) like lower(concat('%', :search, '%')) or " +
            "lower(e.grade) like lower(concat('%', :search, '%')) or " +
            "lower(e.phone) like lower(concat('%', :search, '%')) or " +
            "lower(e.email) like lower(concat('%', :search, '%')))")
    Page<Employee> searchEmployees(@Param("search") String search, @Param("activeOnly") Boolean activeOnly, Pageable pageable);
}