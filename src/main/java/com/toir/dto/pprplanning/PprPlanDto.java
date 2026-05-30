package com.toir.dto.pprplanning;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.toir.enums.PlanStatus;
import com.toir.entity.PprPlan;
import com.toir.enums.PprFrequency;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprType;
import io.swagger.v3.oas.annotations.media.Schema;

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
        @JsonIgnore
        List<PprTaskDto> tasks,
        @Schema(description = "Number of non-deleted PPR tasks associated with this plan", example = "12")
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
    public PprPlanDto {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        targets = targets == null ? List.of() : List.copyOf(targets);
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
            LocalDate toDate
    ) {
        this(id, code, name, status, departmentId, departmentName, createdById, approvedById, notes, tasks,
                tasks == null ? 0 : tasks.size(), fromDate, toDate, null, null, null, null, null, List.of());
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
                tasks == null ? 0 : tasks.size(), fromDate, toDate, pprType, scheduleType, frequency,
                intervalHours, scopeType, targets);
    }

    public static PprPlanDto from(PprPlan p) {
        return from(p, null, 0);
    }

    public static PprPlanDto from(PprPlan p, String departmentName) {
        return from(p, departmentName, 0);
    }

    public static PprPlanDto from(PprPlan p, String departmentName, long taskCount) {
        return from(p, departmentName, taskCount, Map.of());
    }

    public static PprPlanDto from(PprPlan p,
                                  String departmentName,
                                  long taskCount,
                                  Map<UUID, Long> taskCounts) {
        long resolvedTaskCount = taskCounts.getOrDefault(p.getId(), taskCount);
        return new PprPlanDto(
                p.getId(), p.getCode(), p.getName(), p.getStatus(),
                p.getDepartmentId(), departmentName, p.getCreatedById(), p.getApprovedById(), p.getNotes(),
                List.of(),
                resolvedTaskCount,
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
