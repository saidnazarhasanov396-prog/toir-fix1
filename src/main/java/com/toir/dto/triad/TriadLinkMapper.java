package com.toir.dto.triad;

import com.toir.entity.defects.Defect;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;

public final class TriadLinkMapper {

    private TriadLinkMapper() {
    }

    public static RepairRequestBriefDto toRepairRequestBrief(RepairRequest repairRequest) {
        if (repairRequest == null) {
            return null;
        }
        return new RepairRequestBriefDto(
                repairRequest.getId(),
                repairRequest.getNumber(),
                repairRequest.getStatus(),
                repairRequest.getPriority(),
                repairRequest.getTitle(),
                repairRequest.getDescription()
        );
    }

    public static DefectBriefDto toDefectBrief(Defect defect) {
        if (defect == null) {
            return null;
        }
        return new DefectBriefDto(
                defect.getId(),
                defect.getCode(),
                defect.getTitle(),
                defect.getStatus(),
                defect.getSeverity(),
                defect.getCreatedAt()
        );
    }

    public static WorkOrderBriefDto toWorkOrderBrief(WorkOrder workOrder) {
        if (workOrder == null) {
            return null;
        }
        return new WorkOrderBriefDto(
                workOrder.getId(),
                workOrder.getNumber(),
                workOrder.getStatus(),
                workOrder.getWorkType(),
                workOrder.getPriority(),
                workOrder.getCreatedAt(),
                workOrder.getStartPlannedAt()
        );
    }
}
