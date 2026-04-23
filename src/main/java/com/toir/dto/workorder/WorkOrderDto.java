package com.toir.dto.workorder;
import com.toir.dto.workorder.WorkOrderTaskDto;

import com.toir.entity.PriorityLevel;
import com.toir.entity.WorkOrder;
import com.toir.entity.WorkOrderStatus;
import com.toir.entity.WorkOrderType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkOrderDto(
        UUID id,
        String number,
        String title,
        UUID equipmentId,
        UUID departmentId,
        UUID repairRequestId,
        UUID pprTaskId,
        UUID contractorId,
        WorkOrderStatus status,
        WorkOrderType type,
        PriorityLevel priority,
        Instant startPlannedAt,
        Instant endPlannedAt,
        Instant startedAt,
        Instant completedAt,
        String summary,
        String result,
        String closureNotes,
        UUID createdById,
        UUID approvedById,
        List<WorkOrderTaskDto> tasks
) {
    public static WorkOrderDto from(WorkOrder w) {
        return new WorkOrderDto(
                w.getId(), w.getNumber(), w.getTitle(), w.getEquipmentId(), w.getDepartmentId(),
                w.getRepairRequestId(), w.getPprTaskId(), w.getContractorId(),
                w.getStatus(), w.getType(), w.getPriority(),
                w.getStartPlannedAt(), w.getEndPlannedAt(), w.getStartedAt(), w.getCompletedAt(),
                w.getSummary(), w.getResult(), w.getClosureNotes(),
                w.getCreatedById(), w.getApprovedById(),
                w.getTasks().stream().map(WorkOrderTaskDto::from).toList()
        );
    }
}
