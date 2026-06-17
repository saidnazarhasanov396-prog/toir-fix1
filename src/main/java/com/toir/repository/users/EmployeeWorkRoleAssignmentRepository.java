package com.toir.repository.users;

import com.toir.entity.users.EmployeeWorkRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeWorkRoleAssignmentRepository extends JpaRepository<EmployeeWorkRoleAssignment, UUID> {

    @Query("""
            select count(a) > 0
            from EmployeeWorkRoleAssignment a
            join a.workRole r
            where a.employeeId = :employeeId
              and a.isDeleted = false
              and r.isDeleted = false
              and r.active = true
              and upper(r.code) = upper(:workRoleCode)
            """)
    boolean existsActiveByEmployeeIdAndWorkRoleCode(
            @Param("employeeId") UUID employeeId,
            @Param("workRoleCode") String workRoleCode
    );

    List<EmployeeWorkRoleAssignment> findAllByEmployeeIdAndIsDeletedFalse(UUID employeeId);

    @Query(value = """
            select a.employee_id as employeeId, r.code as code
            from hr_employee_work_role_assignments a
            join employee_work_roles r on r.id = a.work_role_id
            where a.is_deleted = false
              and r.is_deleted = false
              and r.is_active = true
              and a.employee_id in (:employeeIds)
            order by r.code asc
            """, nativeQuery = true)
    List<EmployeeWorkRoleCodeProjection> findActiveWorkRoleCodesByEmployeeIds(
            @Param("employeeIds") Collection<UUID> employeeIds
    );
}
