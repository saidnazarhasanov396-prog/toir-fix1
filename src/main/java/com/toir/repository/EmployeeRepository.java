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
    java.util.Optional<Employee> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Employee> findAllByIsDeletedFalse();

    java.util.List<Employee> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM hr_employees WHERE personnel_number = :personnelNumber AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Employee> findByPersonnelNumberAndIsDeletedFalse(@Param("personnelNumber") String personnelNumber);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM hr_employees WHERE personnel_number = :personnelNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByPersonnelNumberAndIsDeletedFalse(@Param("personnelNumber") String personnelNumber);

    @Query(value = "SELECT * FROM hr_employees WHERE is_active = true AND is_deleted = false", nativeQuery = true)
    List<Employee> findAllByActiveTrueAndIsDeletedFalse();

    @Query(value = "SELECT * FROM hr_employees WHERE department_id = :departmentId AND is_deleted = false", nativeQuery = true)
    List<Employee> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM hr_employees WHERE brigade_id = :brigadeId AND is_deleted = false", nativeQuery = true)
    List<Employee> findAllByBrigadeIdAndIsDeletedFalse(@Param("brigadeId") UUID brigadeId);

    @Query("select e from Employee e where " +
            "(cast(:activeOnly as string) is null or " +
            "(:activeOnly = true and e.isDeleted = false) or " +
            "(:activeOnly = false and e.isDeleted = true)) " +
            "and (cast(:search as string) is null or " +
            "lower(e.personnelNumber) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.firstName) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.lastName) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.middleName) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.position) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.grade) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.phone) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.email) like lower(concat('%', cast(:search as string), '%')))")
    Page<Employee> searchEmployees(@Param("search") String search, @Param("activeOnly") Boolean activeOnly, Pageable pageable);
}