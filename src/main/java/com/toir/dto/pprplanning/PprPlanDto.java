package com.toir.dto.pprplanning;
import com.toir.dto.pprplanning.PprTaskDto;

import com.toir.entity.PlanStatus;
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
        UUID createdById,
        UUID approvedById,
        String notes,
        List<PprTaskDto> tasks
) {
    public static PprPlanDto from(PprPlan p) {
        return new PprPlanDto(
                p.getId(), p.getCode(), p.getName(), p.getYear(), p.getMonth(), p.getStatus(),
                p.getDepartmentId(), p.getCreatedById(), p.getApprovedById(), p.getNotes(),
                p.getTasks().stream().map(PprTaskDto::from).toList()
        );
    }
}
