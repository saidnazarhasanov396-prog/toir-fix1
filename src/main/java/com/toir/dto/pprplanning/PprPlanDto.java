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
        long taskCount,
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
                countTasks(tasks), fromDate, toDate, null, null, null, null, null, List.of());
    }

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
            long taskCount,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        this(id, code, name, status, departmentId, departmentName, createdById, approvedById, notes, List.of(),
                taskCount, fromDate, toDate, null, null, null, null, null, List.of());
    }

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
            LocalDate toDate,
            PprType pprType,
            PprScheduleType scheduleType,
            PprFrequency frequency,
            Long intervalHours,
            PprScopeType scopeType,
            List<PprPlanTargetDto> targets
    ) {
        this(id, code, name, status, departmentId, departmentName, createdById, approvedById, notes, tasks,
                countTasks(tasks), fromDate, toDate, pprType, scheduleType, frequency, intervalHours, scopeType, targets);
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
        return from(p, departmentName, ruleById, Map.of(), Map.of());
    }

    public static PprPlanDto from(PprPlan p,
                                  String departmentName,
                                  Map<UUID, EquipmentMaintenanceRule> ruleById,
                                  Map<UUID, String> equipmentNames,
                                  Map<UUID, String> regulationNames) {
        List<PprTaskDto> tasks = p.getTasks().stream()
                .map(task -> PprTaskDto.from(task, ruleById, equipmentNames, regulationNames))
                .toList();
        return new PprPlanDto(
                p.getId(), p.getCode(), p.getName(), p.getStatus(),
                p.getDepartmentId(), departmentName, p.getCreatedById(), p.getApprovedById(), p.getNotes(),
                tasks,
                tasks.size(),
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

    private static long countTasks(List<PprTaskDto> tasks) {
        return tasks == null ? 0 : tasks.size();
    }
}
