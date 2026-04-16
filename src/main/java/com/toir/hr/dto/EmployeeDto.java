package com.toir.hr.dto;

import com.toir.hr.Employee;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeDto(
        UUID id,
        String personnelNumber,
        String firstName,
        String lastName,
        String middleName,
        String position,
        UUID departmentId,
        UUID brigadeId,
        UUID userId,
        LocalDate hireDate,
        LocalDate terminatedDate,
        String grade,
        String phone,
        String email,
        boolean active
) {
    public static EmployeeDto from(Employee e) {
        return new EmployeeDto(
                e.getId(), e.getPersonnelNumber(), e.getFirstName(), e.getLastName(), e.getMiddleName(),
                e.getPosition(), e.getDepartmentId(), e.getBrigadeId(), e.getUserId(),
                e.getHireDate(), e.getTerminatedDate(), e.getGrade(), e.getPhone(), e.getEmail(),
                e.isActive());
    }
}
