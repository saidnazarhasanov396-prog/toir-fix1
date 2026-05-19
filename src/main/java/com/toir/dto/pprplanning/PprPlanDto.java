package com.toir.dto.pprplanning;

import com.toir.enums.PlanStatus;
import com.toir.entity.PprPlan;

import java.util.List;
import java.util.UUID;

public record PprPlanDto(
        UUID id,
        String code,
        String name,
        int year,
        int month,
        PlanStatus status,
        UUID departmentId,
        String departmentName,
        UUID createdById,
        UUID approvedById,
        String notes,
        List<PprTaskDto> tasks
) {
    public static PprPlanDto from(PprPlan p) {
        return from(p, null);
    }

    public static PprPlanDto from(PprPlan p, String departmentName) {
        return new PprPlanDto(
                p.getId(), p.getCode(), p.getName(), p.getYear(), p.getMonth(), p.getStatus(),
                p.getDepartmentId(), departmentName, p.getCreatedById(), p.getApprovedById(), p.getNotes(),
                p.getTasks().stream().map(PprTaskDto::from).toList()
        );
    }
}
