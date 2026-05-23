package com.toir.dto.pprplanning;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.toir.enums.PlanStatus;
import com.toir.entity.PprPlan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PprPlanDto(
        UUID id,
        String code,
        String name,
        PlanStatus status,
        UUID departmentId,
        String departmentName,
        UUID createdById,
        UUID approvedById,
        String notes,
        List<PprTaskDto> tasks,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate fromDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate toDate
) {
    public static PprPlanDto from(PprPlan p) {
        return from(p, null);
    }

    public static PprPlanDto from(PprPlan p, String departmentName) {
        return new PprPlanDto(
                p.getId(), p.getCode(), p.getName(), p.getStatus(),
                p.getDepartmentId(), departmentName, p.getCreatedById(), p.getApprovedById(), p.getNotes(),
                p.getTasks().stream().map(PprTaskDto::from).toList(),
                p.getStartDate(),
                p.getEndDate()
        );
    }
}
