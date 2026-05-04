package com.toir.entity.users;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hr_employees")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Employee extends BaseEntity {

    @Column(name = "personnel_number", nullable = false, unique = true)
    private String personnelNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "position", nullable = false)
    private String position;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "brigade_id")
    private UUID brigadeId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "terminated_date")
    private LocalDate terminatedDate;

    @Column(name = "grade")
    private String grade;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

}
