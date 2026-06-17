package com.toir.dto.hr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmployeeRequest(
        @NotBlank String personnelNumber,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String middleName,
        @NotBlank String position,
        UUID departmentId,
        UUID brigadeId,
        UUID userId,
        @NotNull LocalDate hireDate,
        LocalDate terminatedDate,
        String grade,
        String phone,
        String email,
        Boolean active,
        List<String> workRoleCodes
) {
    public EmployeeRequest(
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
            Boolean active
    ) {
        this(
                personnelNumber,
                firstName,
                lastName,
                middleName,
                position,
                departmentId,
                brigadeId,
                userId,
                hireDate,
                terminatedDate,
                grade,
                phone,
                email,
                active,
                null
        );
    }
}
