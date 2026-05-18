package com.toir.dto.hr;

import com.toir.entity.users.Employee;

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
        String departmentName,
        UUID brigadeId,
        String brigadeName,
        UUID userId,
        LocalDate hireDate,
        LocalDate terminatedDate,
        String grade,
        String phone,
        String email,
        boolean active
) {
    public static EmployeeDto from(Employee e, String departmentName, String brigadeName) {
        return new EmployeeDto(
                e.getId(),
                e.getPersonnelNumber(),
                e.getFirstName(),
                e.getLastName(),
                e.getMiddleName(),
                e.getPosition(),
                e.getDepartmentId(),
                departmentName,
                e.getBrigadeId(),
                brigadeName,
                e.getUserId(),
                e.getHireDate(),
                e.getTerminatedDate(),
                e.getGrade(),
                e.getPhone(),
                e.getEmail(),
                e.isActive());
    }
}
