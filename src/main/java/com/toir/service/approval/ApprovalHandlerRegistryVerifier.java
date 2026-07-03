package com.toir.service.approval;

import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.exception.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApprovalHandlerRegistryVerifier implements ApplicationRunner {

    private final List<ApprovalActionHandler> handlers;

    @Override
    public void run(ApplicationArguments args) {
        List<HandlerContract> contracts = new ArrayList<>();
        addApproveReject(contracts, ApprovalTargetType.WORK_ORDER);
        addApproveReject(contracts, ApprovalTargetType.PPR_PLAN);
        contracts.add(new HandlerContract(ApprovalTargetType.MAINTENANCE_DUE_EVENT, ApprovalActionType.CREATE_TASK));
        contracts.add(new HandlerContract(ApprovalTargetType.MAINTENANCE_DUE_EVENT, ApprovalActionType.CREATE_WORK_ORDER));
        addApproveReject(contracts, ApprovalTargetType.PROCUREMENT_REQUEST);
        addApproveReject(contracts, ApprovalTargetType.MAINTENANCE_BUDGET);
        addApproveReject(contracts, ApprovalTargetType.REPAIR_REQUEST);
        addApproveReject(contracts, ApprovalTargetType.MAINTENANCE_REGULATION);
        addApproveReject(contracts, ApprovalTargetType.REGULATION_CHANGE_PROPOSAL);
        addApproveReject(contracts, ApprovalTargetType.ACTUAL_COST);
        addApproveReject(contracts, ApprovalTargetType.DEFECT_LIST);
        addApproveReject(contracts, ApprovalTargetType.PLANNED_SHUTDOWN);
        addApproveReject(contracts, ApprovalTargetType.REPAIR_CAMPAIGN);
        addApproveReject(contracts, ApprovalTargetType.EQUIPMENT_COMMISSIONING);
        addApproveReject(contracts, ApprovalTargetType.WAREHOUSE_WRITEOFF);

        for (HandlerContract contract : contracts) {
            long matches = handlers.stream()
                    .filter(handler -> handler.supports(contract.targetType(), contract.actionType()))
                    .count();
            if (matches != 1) {
                throw RestException.conflict("Expected exactly one approval handler for "
                        + contract.targetType() + " / " + contract.actionType()
                        + " but found " + matches);
            }
        }
    }

    private void addApproveReject(List<HandlerContract> contracts, ApprovalTargetType targetType) {
        contracts.add(new HandlerContract(targetType, ApprovalActionType.APPROVE));
        contracts.add(new HandlerContract(targetType, ApprovalActionType.REJECT));
    }

    private record HandlerContract(ApprovalTargetType targetType, ApprovalActionType actionType) {
    }
}
