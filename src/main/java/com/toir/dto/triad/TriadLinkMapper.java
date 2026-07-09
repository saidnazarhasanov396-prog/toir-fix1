package com.toir.dto.triad;

import com.toir.dto.attachment.AttachmentPhotoSummary;
import com.toir.entity.defects.Defect;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;

public final class TriadLinkMapper {

    private TriadLinkMapper() {
    }

    public static RepairRequestBriefDto toRepairRequestBrief(RepairRequest repairRequest) {
        return toRepairRequestBrief(repairRequest, null, null, null);
    }

    public static RepairRequestBriefDto toRepairRequestBrief(RepairRequest repairRequest,
                                                              String assigneeName,
                                                              String departmentName,
                                                              String locationName) {
        if (repairRequest == null) {
            return null;
        }
        return new RepairRequestBriefDto(
                repairRequest.getId(),
                repairRequest.getNumber(),
                repairRequest.getStatus(),
                repairRequest.getPriority(),
                repairRequest.getTitle(),
                repairRequest.getDescription(),
                assigneeName,
                departmentName,
                locationName,
                repairRequest.getCreatedAt(),
                repairRequest.getTargetCompletionAt(),
                repairRequest.getActualCompletionAt()
        );
    }

    public static DefectBriefDto toDefectBrief(Defect defect) {
        return toDefectBrief(defect, null);
    }

    public static DefectBriefDto toDefectBrief(Defect defect, AttachmentPhotoSummary photoSummary) {
        if (defect == null) {
            return null;
        }
        return new DefectBriefDto(
                defect.getId(),
                defect.getCode(),
                defect.getTitle(),
                defect.getStatus(),
                defect.getSeverity(),
                defect.getCreatedAt(),
                photoSummary == null ? 0 : photoSummary.photoCount(),
                photoSummary == null ? null : photoSummary.primaryPhotoDownloadUrl()
        );
    }

    public static WorkOrderBriefDto toWorkOrderBrief(WorkOrder workOrder) {
        return toWorkOrderBrief(workOrder, null);
    }

    public static WorkOrderBriefDto toWorkOrderBrief(WorkOrder workOrder, String assigneeName) {
        if (workOrder == null) {
            return null;
        }
        return new WorkOrderBriefDto(
                workOrder.getId(),
                workOrder.getNumber(),
                workOrder.getTitle(),
                workOrder.getSummary(),
                assigneeName,
                workOrder.getStatus(),
                workOrder.getWorkType(),
                workOrder.getPriority(),
                workOrder.getCreatedAt(),
                workOrder.getStartPlannedAt(),
                workOrder.getEndPlannedAt(),
                workOrder.getCompletedAt()
        );
    }
}
