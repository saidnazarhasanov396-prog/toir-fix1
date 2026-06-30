package com.toir.service.counteragent;

import com.toir.dto.counteragent.CounteragentWorkDto;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ContractorWorkStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorContractRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.service.CounteragentService;
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
public class CounteragentWorkService {

    private static final Set<WorkOrderStatus> WORK_ORDER_EXECUTION_STATUSES =
            EnumSet.of(WorkOrderStatus.APPROVED, WorkOrderStatus.IN_PROGRESS);
    private static final Set<WorkOrderStatus> WORK_ORDER_ACCEPTANCE_STATUSES =
            EnumSet.of(WorkOrderStatus.APPROVED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED);
    private static final String DEFAULT_COUNTERAGENT_COST_CATEGORY_CODE = "CTR";

    private final ContractorWorkRepository repository;
    private final ContractorContractRepository contractRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ActualCostRepository actualCostRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final FinanceScopeService financeScopeService;
    private final CounteragentService counteragentService;
    private final AuditBuilderService auditBuilderService;
    private final RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;

    @Transactional(readOnly = true)
    public List<CounteragentWorkDto> findByCounteragent(UUID counteragentId) {
        return repository.findAllByOptionalCounteragentIdAndIsDeletedFalseOrderByUpdatedAtDesc(counteragentId)
                .stream()
                .map(CounteragentWorkDto::from)
                .toList();
    }

    @Transactional
    public CounteragentWorkDto create(CounteragentWorkDto request) {
        counteragentService.loadActive(request.counteragentId(), "counteragent work");
        assertValidContract(request.counteragentId());
        WorkOrder workOrder = requireLinkedWorkOrder(request.workOrderId());
        assertWorkOrderAllowsCounteragentWork(workOrder);
        assignWorkOrderToCounteragent(workOrder, request.counteragentId());

        ContractorWork work = new ContractorWork();
        work.setCounteragentId(request.counteragentId());
        work.setWorkOrderId(request.workOrderId());
        work.setDescription(request.description());
        work.setCost(request.cost());
        ContractorWork saved = repository.save(work);
        auditBuilderService.log(
                "counteragent_work",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.COUNTERAGENT_WORK,
                "Counteragent work created",
                null,
                saved
        );
        return CounteragentWorkDto.from(saved);
    }

    @Transactional
    public CounteragentWorkDto start(UUID id) {
        ContractorWork work = getOrThrow(id);
        assertCanStart(work);
        counteragentService.loadActive(work.getCounteragentId(), "counteragent work lifecycle actions");
        assertValidContract(work.getCounteragentId());
        WorkOrder workOrder = requireLinkedWorkOrder(work.getWorkOrderId());
        assertWorkOrderAllowsCounteragentWork(workOrder);

        work.setStatus(ContractorWorkStatus.IN_PROGRESS);
        work.setStartedAt(Instant.now());
        ContractorWork saved = repository.save(work);
        auditBuilderService.log(
                "counteragent_work",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.COUNTERAGENT_WORK,
                "Counteragent work updated",
                work,
                saved
        );
        return CounteragentWorkDto.from(saved);
    }

    @Transactional
    public CounteragentWorkDto complete(UUID id, String result, Double cost) {
        ContractorWork work = getOrThrow(id);
        assertCanComplete(work, result);
        work.setStatus(ContractorWorkStatus.COMPLETED);
        work.setCompletedAt(Instant.now());
        work.setResult(result.trim());
        work.setCost(cost);
        ContractorWork saved = repository.save(work);
        auditBuilderService.log(
                "counteragent_work",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.COUNTERAGENT_WORK,
                "Counteragent work updated",
                work,
                saved
        );
        return CounteragentWorkDto.from(saved);
    }

    @Transactional
    public CounteragentWorkDto accept(UUID id, UUID acceptedById, String comment) {
        ContractorWork work = getOrThrow(id);
        assertCanAccept(work, acceptedById, comment);
        counteragentService.loadActive(work.getCounteragentId(), "counteragent work lifecycle actions");
        assertValidContract(work.getCounteragentId());
        WorkOrder workOrder = requireLinkedWorkOrder(work.getWorkOrderId());
        assertWorkOrderAllowsAcceptance(workOrder);
        createOrReusePendingActualCost(work);

        work.setStatus(ContractorWorkStatus.ACCEPTED);
        work.setAcceptedById(acceptedById);
        work.setAcceptedAt(Instant.now());
        work.setAcceptanceComment(comment.trim());
        ContractorWork saved = repository.save(work);
        auditBuilderService.log(
                "counteragent_work",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.COUNTERAGENT_WORK,
                "Counteragent work updated",
                work,
                saved
        );
        return CounteragentWorkDto.from(saved);
    }

    private void assignWorkOrderToCounteragent(WorkOrder workOrder, UUID counteragentId) {
        if (workOrder.getCounteragentId() != null && !Objects.equals(workOrder.getCounteragentId(), counteragentId)) {
            throw RestException.badRequest("Linked work order is already assigned to another counteragent");
        }
        if (!Objects.equals(workOrder.getCounteragentId(), counteragentId)) {
            workOrder.setCounteragentId(counteragentId);
            workOrderRepository.save(workOrder);
        }
    }

    private void assertValidContract(UUID counteragentId) {
        if (!contractRepository.existsActiveCounteragentContractValidOn(counteragentId, LocalDate.now())) {
            throw RestException.badRequest("Counteragent must have at least one ACTIVE contract valid by date");
        }
    }

    private WorkOrder requireLinkedWorkOrder(UUID workOrderId) {
        if (workOrderId == null) {
            throw RestException.badRequest("Linked work order is required for counteragent work lifecycle actions");
        }
        return workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
    }

    private void assertWorkOrderAllowsCounteragentWork(WorkOrder workOrder) {
        if (!WORK_ORDER_EXECUTION_STATUSES.contains(workOrder.getStatus())) {
            throw RestException.badRequest("Linked work order must be APPROVED or IN_PROGRESS for counteragent work execution");
        }
    }

    private void assertWorkOrderAllowsAcceptance(WorkOrder workOrder) {
        if (!WORK_ORDER_ACCEPTANCE_STATUSES.contains(workOrder.getStatus())) {
            throw RestException.badRequest("Linked work order status does not allow counteragent work acceptance: " + workOrder.getStatus());
        }
    }

    private ContractorWork getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Counteragent work not found: " + id));
    }

    private void assertCanStart(ContractorWork work) {
        if (work.getStatus() != ContractorWorkStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT counteragent works can be started");
        }
    }

    private void assertCanComplete(ContractorWork work, String result) {
        if (work.getStatus() != ContractorWorkStatus.IN_PROGRESS) {
            throw RestException.badRequest("Only IN_PROGRESS counteragent works can be completed");
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
            throw RestException.badRequest("acceptedById is required to accept counteragent work");
        }
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest("Acceptance comment is required to accept counteragent work");
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
            throw RestException.badRequest("Cannot auto-create actual cost for counteragent work without linked work order");
        }
        ActualCost cost = new ActualCost();
        cost.setSourceType(ActualCostSourceType.COUNTERAGENT_WORK);
        cost.setSourceId(work.getId());
        cost.setWorkOrderId(work.getWorkOrderId());
        cost.setContractorWorkId(work.getId());
        cost.setBudgetLineId(repairCampaignBudgetLineResolver.resolveForWorkOrderId(work.getWorkOrderId()));
        cost.setCostCategoryId(resolveCostCategoryId(work));
        cost.setAmount(work.getCost());
        cost.setStatus(ActualCostStatus.PENDING);
        cost.setNotes("Auto-created from counteragent work acceptance: " + work.getId());
        financeScopeService.assertCanMutateActualCost(cost);
        return actualCostRepository.save(cost);
    }

    private UUID resolveCostCategoryId(ContractorWork work) {
        if (work.getWorkOrderId() != null) {
            UUID fromWorkOrder = actualCostRepository
                    .findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(work.getWorkOrderId())
                    .stream()
                    .map(ActualCost::getCostCategoryId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (fromWorkOrder != null) {
                return fromWorkOrder;
            }
        }
        return costCategoryRepository.findFirstByCodeAndIsDeletedFalse(DEFAULT_COUNTERAGENT_COST_CATEGORY_CODE)
                .map(category -> category.getId())
                .orElseThrow(() -> RestException.badRequest(
                        "Cannot auto-create actual cost for counteragent work; counteragent cost category is missing"));
    }
}
