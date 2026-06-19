package com.toir.dto.hr;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeFilterRequest(
        String search,
        Boolean activeOnly,
        UUID departmentId,
        UUID brigadeId,
        String workRoleCode,
        String personnelNumber,
        String firstName,
        String lastName,
        String middleName,
        String position,
        UUID userId,
        UUID specialisationId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hireDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hireDateTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate terminatedDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate terminatedDateTo,
        String grade,
        String phone,
        String email
) {
    public EmployeeFilterRequest withDepartmentId(UUID departmentId) {
        return new EmployeeFilterRequest(
                search,
                activeOnly,
                departmentId,
                brigadeId,
                workRoleCode,
                personnelNumber,
                firstName,
                lastName,
                middleName,
                position,
                userId,
                specialisationId,
                hireDateFrom,
                hireDateTo,
                terminatedDateFrom,
                terminatedDateTo,
                grade,
                phone,
                email
        );
    }
}
