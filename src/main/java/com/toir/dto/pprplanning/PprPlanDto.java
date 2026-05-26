package com.toir.dto.pprplanning;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.toir.enums.PlanStatus;
import com.toir.entity.PprPlan;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.enums.PprFrequency;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
        LocalDate toDate,
        PprType pprType,
        PprScheduleType scheduleType,
        PprFrequency frequency,
        Long intervalHours,
        PprScopeType scopeType,
        List<PprPlanTargetDto> targets
) {
    public PprPlanDto(
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
            LocalDate fromDate,
            LocalDate toDate
    ) {
        this(id, code, name, status, departmentId, departmentName, createdById, approvedById, notes, tasks,
                fromDate, toDate, null, null, null, null, null, List.of());
    }

    public static PprPlanDto from(PprPlan p) {
        return from(p, null);
    }

    public static PprPlanDto from(PprPlan p, String departmentName) {
        return from(p, departmentName, Map.of());
    }

    public static PprPlanDto from(PprPlan p,
                                  String departmentName,
                                  Map<UUID, EquipmentMaintenanceRule> ruleById) {
        return new PprPlanDto(
                p.getId(), p.getCode(), p.getName(), p.getStatus(),
                p.getDepartmentId(), departmentName, p.getCreatedById(), p.getApprovedById(), p.getNotes(),
                p.getTasks().stream().map(task -> PprTaskDto.from(task, ruleById)).toList(),
                p.getStartDate(),
                p.getEndDate(),
                p.getPprType(),
                p.getScheduleType(),
                p.getFrequency(),
                p.getIntervalHours(),
                p.getScopeType(),
                p.getTargets().stream().map(PprPlanTargetDto::from).toList()
        );
    }
}
