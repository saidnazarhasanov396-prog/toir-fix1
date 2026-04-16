package com.toir.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
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
        Boolean active
) {}
