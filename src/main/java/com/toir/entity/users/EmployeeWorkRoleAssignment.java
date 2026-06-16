package com.toir.entity.users;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "hr_employee_work_role_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_role_id"}),
        indexes = {
                @Index(name = "idx_employee_work_role_assignments_employee", columnList = "employee_id"),
                @Index(name = "idx_employee_work_role_assignments_role", columnList = "work_role_id")
        }
)
@Getter
@Setter
public class EmployeeWorkRoleAssignment extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "work_role_id", nullable = false)
    private UUID workRoleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_role_id", insertable = false, updatable = false)
    private EmployeeWorkRole workRole;
}
