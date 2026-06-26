package com.toir.service.contactor;

import com.toir.dto.contractorwork.ContractorWorkDto;
import com.toir.entity.contractors.Contractor;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.ContractorStatus;
import com.toir.enums.ContractorWorkStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorContractRepository;
import com.toir.repository.contarctor.ContractorRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.service.FinanceScopeService;
import com.toir.service.repair.RepairCampaignBudgetLineResolver;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractorWorkService {

    private static final Set<WorkOrderStatus> WORK_ORDER_EXECUTION_STATUSES =
            EnumSet.of(WorkOrderStatus.APPROVED, WorkOrderStatus.IN_PROGRESS);
    private static final Set<WorkOrderStatus> WORK_ORDER_ACCEPTANCE_STATUSES =
            EnumSet.of(WorkOrderStatus.APPROVED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED);
    private static final String DEFAULT_CONTRACTOR_COST_CATEGORY_CODE = "CTR";

    private final ContractorWorkRepository repository;
    private final ContractorRepository contractorRepository;
    private final ContractorContractRepository contractorContractRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ActualCostRepository actualCostRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final FinanceScopeService financeScopeService;
    private final AuditBuilderService auditBuilderService;
    private final RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;

    @Transactional(readOnly = true)
    public List<ContractorWorkDto> findByContractor(UUID contractorId) {
        return repository.findAllByOptionalContractorIdAndIsDeletedFalseOrderByUpdatedAtDesc(contractorId)
                .stream()
                .map(ContractorWorkDto::from)
                .toList();
    }

    @Transactional
    public ContractorWorkDto create(ContractorWorkDto r) {
        assertActiveContractor(r.contractorId());
        assertValidContract(r.contractorId());
        WorkOrder workOrder = requireLinkedWorkOrder(r.workOrderId());
        assertWorkOrderAllowsContractorWork(workOrder);
        assignWorkOrderToContractor(workOrder, r.contractorId());

        ContractorWork w = new ContractorWork();
        w.setContractorId(r.contractorId());
        w.setWorkOrderId(r.workOrderId());
        w.setDescription(r.description());
        w.setCost(r.cost());
        ContractorWork saved = repository.save(w);

        auditBuilderService.log(
                "contractor_work",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.CONTRACTOR_WORK,
                "Работа подрядчика создана",
                null,
                saved
        );

        return ContractorWorkDto.from(saved);
    }

    private void assignWorkOrderToContractor(WorkOrder workOrder, UUID contractorId) {
        if (workOrder.getContractorId() != null && !Objects.equals(workOrder.getContractorId(), contractorId)) {
            throw RestException.badRequest("Linked work order is already assigned to another contractor");
        }
        if (!Objects.equals(workOrder.getContractorId(), contractorId)) {
            workOrder.setContractorId(contractorId);
            workOrderRepository.save(workOrder);
        }
    }

    @Transactional
    public ContractorWorkDto start(UUID id) {
        ContractorWork w = getOrThrow(id);
        assertCanStart(w);
        assertActiveContractor(w.getContractorId());
        assertValidContract(w.getContractorId());
        WorkOrder workOrder = requireLinkedWorkOrder(w.getWorkOrderId());
        assertWorkOrderAllowsContractorWork(workOrder);

        w.setStatus(ContractorWorkStatus.IN_PROGRESS);
        w.setStartedAt(Instant.now());
        ContractorWork saved = repository.save(w);

        auditBuilderService.log(
                "contractor_work",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CONTRACTOR_WORK,
                "Работа подрядчика обновлена",
                w,
                saved
        );

        return ContractorWorkDto.from(w);
    }

    @Transactional
    public ContractorWorkDto complete(UUID id, String result, Double cost) {
        ContractorWork w = getOrThrow(id);
        assertCanComplete(w, result);

        w.setStatus(ContractorWorkStatus.COMPLETED);
        w.setCompletedAt(Instant.now());
        w.setResult(result.trim());
        w.setCost(cost);

        ContractorWork save = repository.save(w);

        auditBuilderService.log(
                "contractor_work",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CONTRACTOR_WORK,
                "Работа подрядчика обновлена",
                w,
                save
        );

        return ContractorWorkDto.from(w);
    }

    @Transactional
    public ContractorWorkDto accept(UUID id, UUID acceptedById, String comment) {
        ContractorWork w = getOrThrow(id);
        assertCanAccept(w, acceptedById, comment);
        assertActiveContractor(w.getContractorId());
        assertValidContract(w.getContractorId());
        WorkOrder workOrder = requireLinkedWorkOrder(w.getWorkOrderId());
        assertWorkOrderAllowsAcceptance(workOrder);
        createOrReusePendingActualCost(w);

        w.setStatus(ContractorWorkStatus.ACCEPTED);
        w.setAcceptedById(acceptedById);
        w.setAcceptedAt(Instant.now());
        w.setAcceptanceComment(comment.trim());


        ContractorWork saved = repository.save(w);

        auditBuilderService.log(
                "contractor_work",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CONTRACTOR_WORK,
                "Работа подрядчика обновлена",
                w,
                saved
        );
        return ContractorWorkDto.from(w);
    }

    private ContractorWork getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Contractor work not found: " + id));
    }

    private Contractor assertActiveContractor(UUID contractorId) {
        Contractor contractor = contractorRepository.findByIdAndIsDeletedFalse(contractorId)
                .orElseThrow(() -> RestException.notFound("Contractor not found: " + contractorId));
        if (contractor.getStatus() != ContractorStatus.ACTIVE) {
            throw RestException.badRequest("Contractor must be ACTIVE for contractor work lifecycle actions");
        }
        return contractor;
    }

    private void assertValidContract(UUID contractorId) {
        LocalDate today = LocalDate.now();
        if (!contractorContractRepository.existsActiveContractValidOn(contractorId, today)) {
            throw RestException.badRequest("Contractor must have at least one ACTIVE contract valid by date");
        }
    }

    private WorkOrder requireLinkedWorkOrder(UUID workOrderId) {
        if (workOrderId == null) {
            throw RestException.badRequest("Linked work order is required for contractor work lifecycle actions");
        }
        return workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
    }

    private void assertWorkOrderAllowsContractorWork(WorkOrder workOrder) {
        if (!WORK_ORDER_EXECUTION_STATUSES.contains(workOrder.getStatus())) {
            throw RestException.badRequest(
                    "Linked work order must be APPROVED or IN_PROGRESS for contractor work execution");
        }
    }

    private void assertWorkOrderAllowsAcceptance(WorkOrder workOrder) {
        if (!WORK_ORDER_ACCEPTANCE_STATUSES.contains(workOrder.getStatus())) {
            throw RestException.badRequest(
                    "Linked work order status does not allow contractor work acceptance: " + workOrder.getStatus());
        }
    }

    private void assertCanStart(ContractorWork work) {
        if (work.getStatus() != ContractorWorkStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT contractor works can be started");
        }
    }

    private void assertCanComplete(ContractorWork work, String result) {
        if (work.getStatus() != ContractorWorkStatus.IN_PROGRESS) {
            throw RestException.badRequest("Only IN_PROGRESS contractor works can be completed");
        }
        if (result == null || result.isBlank()) {
            throw RestException.badRequest("Result is required to complete work");
        }
    }

    private void assertCanAccept(ContractorWork work, UUID acceptedById, String comment) {
        if (work.getStatus() != ContractorWorkStatus.COMPLETED) {
            throw RestException.badRequest("Only completed works can be accepted");
        }
        if (acceptedById == null) {
            throw RestException.badRequest("acceptedById is required to accept contractor work");
        }
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest("Acceptance comment is required to accept contractor work");
        }
    }

    private ActualCost createOrReusePendingActualCost(ContractorWork work) {
        if (work.getCost() == null || work.getCost() <= 0) {
            return null;
        }
        return actualCostRepository.findTopByContractorWorkIdAndIsDeletedFalseOrderByUpdatedAtDesc(work.getId())
                .orElseGet(() -> createPendingActualCost(work));
    }

    private ActualCost createPendingActualCost(ContractorWork work) {
        if (work.getWorkOrderId() == null) {
            throw RestException.badRequest(
                    "Cannot auto-create actual cost for contractor work without linked work order");
        }
        UUID costCategoryId = resolveCostCategoryId(work);
        ActualCost cost = new ActualCost();
        cost.setSourceType(com.toir.enums.ActualCostSourceType.CONTRACTOR_WORK);
        cost.setSourceId(work.getId());
        cost.setWorkOrderId(work.getWorkOrderId());
        cost.setContractorWorkId(work.getId());
        cost.setBudgetLineId(repairCampaignBudgetLineResolver.resolveForWorkOrderId(work.getWorkOrderId()));
        cost.setCostCategoryId(costCategoryId);
        cost.setAmount(work.getCost());
        cost.setStatus(ActualCostStatus.PENDING);
        cost.setNotes("Auto-created from contractor work acceptance: " + work.getId());
        financeScopeService.assertCanMutateActualCost(cost);
        return actualCostRepository.save(cost);
    }

    private UUID resolveCostCategoryId(ContractorWork work) {
        if (work.getWorkOrderId() != null) {
            UUID fromWorkOrder = actualCostRepository
                    .findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(work.getWorkOrderId())
                    .stream()
                    .map(ActualCost::getCostCategoryId)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (fromWorkOrder != null) {
                return fromWorkOrder;
            }
        }
        return costCategoryRepository.findFirstByCodeAndIsDeletedFalse(DEFAULT_CONTRACTOR_COST_CATEGORY_CODE)
                .map(category -> category.getId())
                .orElseThrow(() -> RestException.badRequest(
                        "Cannot auto-create actual cost for contractor work; contractor cost category is missing"));
    }

}
