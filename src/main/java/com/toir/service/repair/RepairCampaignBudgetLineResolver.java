package com.toir.service.repair;

import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairCampaignStage;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.repair.RepairCampaignStageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RepairCampaignBudgetLineResolver {

    private final WorkOrderRepository workOrderRepository;
    private final RepairCampaignStageRepository repairCampaignStageRepository;

    @Transactional(readOnly = true)
    public UUID resolveForWorkOrderId(UUID workOrderId) {
        if (workOrderId == null) {
            return null;
        }
        return workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .map(this::resolveForWorkOrder)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public UUID resolveForWorkOrder(WorkOrder workOrder) {
        if (workOrder == null) {
            return null;
        }
        if (workOrder.getBudgetLineId() != null) {
            return workOrder.getBudgetLineId();
        }
        UUID stageId = workOrder.getRepairCampaignStageId();
        if (stageId == null) {
            return null;
        }
        return repairCampaignStageRepository.findByIdAndIsDeletedFalse(stageId)
                .map(RepairCampaignStage::getBudgetLineId)
                .orElse(null);
    }
}
