package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hr_employees")
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

    public String getPersonnelNumber() { return personnelNumber; }
    public void setPersonnelNumber(String personnelNumber) { this.personnelNumber = personnelNumber; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getBrigadeId() { return brigadeId; }
    public void setBrigadeId(UUID brigadeId) { this.brigadeId = brigadeId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public LocalDate getTerminatedDate() { return terminatedDate; }
    public void setTerminatedDate(LocalDate terminatedDate) { this.terminatedDate = terminatedDate; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
