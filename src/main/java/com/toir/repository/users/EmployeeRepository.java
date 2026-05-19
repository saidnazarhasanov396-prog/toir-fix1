package com.toir.repository.users;

import com.toir.entity.users.Employee;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.toir.repository.projects.EmployeeStatsProjection;
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

    @Query("""
        select e
        from Employee e
        where e.isDeleted = false
          and (:departmentId is null or e.departmentId = :departmentId)
          and (:brigadeId is null or e.brigadeId = :brigadeId)
          and (:activeOnly is null or e.active = :activeOnly)
          and (
                :search is null
                or lower(coalesce(e.personnelNumber, '')) like lower(concat('%', cast(:search as string), '%'))
                or lower(coalesce(e.position, '')) like lower(concat('%', cast(:search as string), '%'))
                or lower(coalesce(e.grade, '')) like lower(concat('%', cast(:search as string), '%'))
                or lower(coalesce(e.phone, '')) like lower(concat('%', cast(:search as string), '%'))
                or lower(coalesce(e.email, '')) like lower(concat('%', cast(:search as string), '%'))
                or lower(coalesce(e.firstName, '')) like lower(concat('%', cast(:search as string), '%'))
                or lower(coalesce(e.lastName, '')) like lower(concat('%', cast(:search as string), '%'))
                or lower(coalesce(e.middleName, '')) like lower(concat('%', cast(:search as string), '%'))
                or (
                    :part1 is not null
                    and :part2 is not null
                    and (
                        (lower(coalesce(e.lastName, '')) like lower(concat('%', cast(:part1 as string), '%'))
                            and lower(coalesce(e.firstName, '')) like lower(concat('%', cast(:part2 as string), '%')))
                        or
                        (lower(coalesce(e.firstName, '')) like lower(concat('%', cast(:part1 as string), '%'))
                            and lower(coalesce(e.lastName, '')) like lower(concat('%', cast(:part2 as string), '%')))
                        or
                        (lower(coalesce(e.firstName, '')) like lower(concat('%', cast(:part1 as string), '%'))
                            and lower(coalesce(e.middleName, '')) like lower(concat('%', cast(:part2 as string), '%')))
                        or
                        (lower(coalesce(e.middleName, '')) like lower(concat('%', cast(:part1 as string), '%'))
                            and lower(coalesce(e.firstName, '')) like lower(concat('%', cast(:part2 as string), '%')))
                        or
                        (lower(coalesce(e.lastName, '')) like lower(concat('%', cast(:part1 as string), '%'))
                            and lower(coalesce(e.middleName, '')) like lower(concat('%', cast(:part2 as string), '%')))
                        or
                        (lower(coalesce(e.middleName, '')) like lower(concat('%', cast(:part1 as string), '%'))
                            and lower(coalesce(e.lastName, '')) like lower(concat('%', cast(:part2 as string), '%')))
                    )
                )
          )
        order by e.updatedAt desc
        """)
    Page<Employee> searchEmployees(
            @Param("search") String search,
            @Param("part1") String part1,
            @Param("part2") String part2,
            @Param("activeOnly") Boolean activeOnly,
            @Param("departmentId") UUID departmentId,
            @Param("brigadeId") UUID brigadeId,
            Pageable pageable
    );

    @Query(nativeQuery = true, value = """
        select
            count(e.id) as total,

            count(*) filter (
                where e.is_active = true
            ) as active,

            count(*) filter (
                where e.is_active = false
                   or e.terminated_date is not null
            ) as terminated,

            count(*) filter (
                where e.email is null
                   or trim(e.email) = ''
            ) as withoutEmail

        from hr_employees e
        where e.is_deleted = false
          and (cast(:departmentId as uuid) is null or e.department_id = cast(:departmentId as uuid))
          and (cast(:brigadeId as uuid) is null or e.brigade_id = cast(:brigadeId as uuid))
          and (
              cast(:searchPattern as varchar) is null
              or lower(coalesce(e.personnel_number, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(e.position, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(e.grade, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(e.phone, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(e.email, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(e.first_name, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(e.last_name, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(e.middle_name, '')) like cast(:searchPattern as varchar)
          )
        """)
    EmployeeStatsProjection getEmployeeStats(
            @Param("departmentId") UUID departmentId,
            @Param("brigadeId") UUID brigadeId,
            @Param("searchPattern") String searchPattern
    );

}
