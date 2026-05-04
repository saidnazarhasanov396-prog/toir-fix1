package com.toir.repository.users;

import com.toir.entity.users.Employee;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    @Query(value = "SELECT * FROM hr_employees WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Employee> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM hr_employees WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Employee> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM hr_employees WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Employee> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM hr_employees WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM hr_employees WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM hr_employees WHERE personnel_number = :personnelNumber AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Employee> findByPersonnelNumberAndIsDeletedFalse(@Param("personnelNumber") String personnelNumber);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM hr_employees WHERE personnel_number = :personnelNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByPersonnelNumberAndIsDeletedFalse(@Param("personnelNumber") String personnelNumber);

    @Query(value = "SELECT * FROM hr_employees WHERE is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Employee> findAllByActiveTrueAndIsDeletedFalse();

    @Query(value = "SELECT * FROM hr_employees WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Employee> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM hr_employees WHERE brigade_id = :brigadeId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Employee> findAllByBrigadeIdAndIsDeletedFalse(@Param("brigadeId") UUID brigadeId);

    @Query("select e from Employee e where " +
            "(cast(:activeOnly as string) is null or " +
            "(:activeOnly = true and e.isDeleted = false) or " +
            "(:activeOnly = false and e.isDeleted = true)) " +
            "and (cast(:search as string) is null or " +
            "lower(e.personnelNumber) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.position) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.grade) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.phone) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.email) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.firstName) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.lastName) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(e.middleName) like lower(concat('%', cast(:search as string), '%')) or " +
            "(" +
            "  (lower(e.lastName) like lower(concat('%', :part1, '%')) and lower(e.firstName) like lower(concat('%', :part2, '%'))) or " +
            "  (lower(e.firstName) like lower(concat('%', :part1, '%')) and lower(e.lastName) like lower(concat('%', :part2, '%'))) or " +
            "  (lower(e.firstName) like lower(concat('%', :part1, '%')) and lower(e.middleName) like lower(concat('%', :part2, '%'))) or " +
            "  (lower(e.middleName) like lower(concat('%', :part1, '%')) and lower(e.firstName) like lower(concat('%', :part2, '%'))) or " +
            "  (lower(e.lastName) like lower(concat('%', :part1, '%')) and lower(e.middleName) like lower(concat('%', :part2, '%'))) or " +
            "  (lower(e.middleName) like lower(concat('%', :part1, '%')) and lower(e.lastName) like lower(concat('%', :part2, '%')))" +
            ")) " +
            "order by e.updatedAt desc")
    Page<Employee> searchEmployees(@Param("search") String search, @Param("part1") String part1, @Param("part2") String part2, @Param("activeOnly") Boolean activeOnly, Pageable pageable);
}
