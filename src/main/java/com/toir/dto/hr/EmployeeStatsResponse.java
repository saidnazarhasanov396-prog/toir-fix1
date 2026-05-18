package com.toir.dto.hr;

public record EmployeeStatsResponse(
        long total,
        long active,
        long terminated,
        long withoutEmail
) {}
