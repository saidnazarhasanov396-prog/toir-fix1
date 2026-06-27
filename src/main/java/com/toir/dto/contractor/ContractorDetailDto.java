package com.toir.dto.contractor;

import com.toir.dto.common.BankAccountDto;
import com.toir.dto.contractorcontract.ContractorContractDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContractorDetailDto(
        UUID id,
        String code,
        String name,
        String taxNumber,
        String contactPerson,
        String phone,
        String email,
        String specialization,
        String directorName,
        List<BankAccountDto> bankAccounts,
        String status,
        List<ContractorContractDto> contracts,
        List<WorkOrderRef> workOrders,
        List<ContractorWorkRef> contractorWorks,
        Summary summary
) {
    public ContractorDetailDto {
        bankAccounts = bankAccounts == null ? List.of() : List.copyOf(bankAccounts);
        contracts = contracts == null ? List.of() : List.copyOf(contracts);
        workOrders = workOrders == null ? List.of() : List.copyOf(workOrders);
        contractorWorks = contractorWorks == null ? List.of() : List.copyOf(contractorWorks);
        summary = summary == null ? Summary.empty() : summary;
    }

    public record Summary(
            long activeContracts,
            long activeWorkOrders,
            long pendingActualCostReview,
            long acceptedAwaitingReflection,
            double totalContractAmount,
            double totalContractorWorkCost,
            double totalReflectedContractorCost,
            double totalActualCost,
            double averageExecutionHours,
            long completedWorks,
            long inProgressWorks,
            long pendingAcceptanceWorks,
            long acceptedWorks,
            long executionLogs
    ) {
        public static Summary empty() {
            return new Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public record WorkOrderRef(
            UUID id,
            String number,
            String title,
            String status,
            Instant createdAt,
            Instant startPlannedAt,
            Instant endPlannedAt,
            EquipmentRef equipment,
            List<ActualCostRef> actualCosts,
            List<ExecutionRef> executions
    ) {
        public WorkOrderRef {
            actualCosts = actualCosts == null ? List.of() : List.copyOf(actualCosts);
            executions = executions == null ? List.of() : List.copyOf(executions);
        }
    }

    public record ContractorWorkRef(
            UUID id,
            UUID contractorId,
            UUID workOrderId,
            String description,
            String status,
            Instant startedAt,
            Instant completedAt,
            Double cost,
            String result,
            String acceptanceComment,
            UUID createdById,
            UUID acceptedById,
            Instant acceptedAt,
            Instant createdAt,
            UserRef createdBy,
            UserRef acceptedBy,
            ContractorWorkOrderRef workOrder,
            List<ActualCostRef> actualCosts
    ) {
        public ContractorWorkRef {
            actualCosts = actualCosts == null ? List.of() : List.copyOf(actualCosts);
        }
    }

    public record ContractorWorkOrderRef(UUID id, String number, String title, String status) {
    }

    public record ActualCostRef(
            UUID id,
            double amount,
            String status,
            Instant createdAt,
            Instant costDate,
            String notes,
            Instant reviewedAt,
            String reviewComment,
            String sourceType,
            UUID sourceId,
            CostCategoryRef costCategory,
            UserRef reviewedBy
    ) {
    }

    public record CostCategoryRef(UUID id, String code, String name, String description) {
    }

    public record EquipmentRef(UUID id, String code, String name) {
    }

    public record ExecutionRef(UUID id, Instant startedAt, Instant endedAt) {
    }

    public record UserRef(UUID id, String fullName) {
    }
}
