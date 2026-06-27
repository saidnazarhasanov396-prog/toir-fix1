package com.toir.service.contactor;

import com.toir.dto.contractor.ContractorDto;
import com.toir.dto.contractor.ContractorDetailDto;
import com.toir.dto.contractor.ContractorRequest;
import com.toir.dto.contractorcontract.ContractorContractDto;
import com.toir.entity.contractors.Contractor;
import com.toir.entity.contractors.ContractorContract;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ContractStatus;
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
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractorService {

    private final ContractorRepository repository;
    private final ContractorContractRepository contractorContractRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ActualCostRepository actualCostRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final EquipmentRepository equipmentRepository;
    private final AuditBuilderService auditBuilderService;

    private static final Set<WorkOrderStatus> ACTIVE_WORK_ORDER_STATUSES = EnumSet.of(
            WorkOrderStatus.DRAFT,
            WorkOrderStatus.PLANNED,
            WorkOrderStatus.APPROVED,
            WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.SUSPENDED
    );

    @Transactional(readOnly = true)
    public List<ContractorDto> findAll(String search) {
        return findAll(search, null, null);
    }

    @Transactional(readOnly = true)
    public List<ContractorDto> findAll(String search, ContractorStatus status, String specialization) {
        return repository.findAllByFilters(search, status, specialization).stream()
                .map(contractor -> ContractorDto.from(contractor, summaryForList(contractor.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractorDto findById(UUID id) {
        return ContractorDto.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public ContractorDetailDto findDetailById(UUID id) {
        Contractor contractor = getOrThrow(id);
        List<ContractorContract> contracts = contractorContractRepository.findAllByContractorIdAndIsDeletedFalse(id);
        List<ContractorWork> contractorWorks = contractorWorkRepository.findAllByContractorIdAndIsDeletedFalse(id);
        List<WorkOrder> workOrders = loadDetailWorkOrders(id, contractorWorks);
        Map<UUID, WorkOrder> workOrderById = workOrders.stream()
                .filter(workOrder -> workOrder.getId() != null)
                .collect(Collectors.toMap(WorkOrder::getId, Function.identity(), (left, ignored) -> left, LinkedHashMap::new));

        List<UUID> contractorWorkIds = contractorWorks.stream()
                .map(ContractorWork::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<ActualCost> actualCosts = contractorWorkIds.isEmpty()
                ? List.of()
                : actualCostRepository.findAllByContractorWorkIdInAndIsDeletedFalseOrderByUpdatedAtDesc(contractorWorkIds);
        Map<UUID, List<ActualCost>> actualCostsByContractorWorkId = actualCosts.stream()
                .filter(cost -> cost.getContractorWorkId() != null)
                .collect(Collectors.groupingBy(ActualCost::getContractorWorkId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<ActualCost>> actualCostsByWorkOrderId = actualCosts.stream()
                .filter(cost -> cost.getWorkOrderId() != null)
                .collect(Collectors.groupingBy(ActualCost::getWorkOrderId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, CostCategory> costCategories = loadCostCategories(actualCosts);
        Map<UUID, Equipment> equipmentById = loadEquipment(workOrders);

        List<ContractorDetailDto.WorkOrderRef> workOrderRefs = workOrders.stream()
                .map(workOrder -> toWorkOrderRef(
                        workOrder,
                        equipmentById.get(workOrder.getEquipmentId()),
                        actualCostsByWorkOrderId.getOrDefault(workOrder.getId(), List.of()),
                        costCategories
                ))
                .toList();
        List<ContractorDetailDto.ContractorWorkRef> contractorWorkRefs = contractorWorks.stream()
                .map(work -> toContractorWorkRef(
                        work,
                        workOrderById.get(work.getWorkOrderId()),
                        actualCostsByContractorWorkId.getOrDefault(work.getId(), List.of()),
                        costCategories
                ))
                .toList();

        return new ContractorDetailDto(
                contractor.getId(),
                contractor.getCode(),
                contractor.getName(),
                contractor.getTaxNumber(),
                contractor.getContactPerson(),
                contractor.getPhone(),
                contractor.getEmail(),
                contractor.getSpecialization(),
                contractor.getDirectorName(),
                contractor.getBankName(),
                contractor.getBankAccount(),
                contractor.getMfo(),
                contractor.getStatus() == null ? null : contractor.getStatus().name(),
                contracts.stream().map(ContractorContractDto::from).toList(),
                workOrderRefs,
                contractorWorkRefs,
                detailSummary(contracts, workOrders, contractorWorks, actualCosts)
        );
    }

    @Transactional
    public ContractorDto create(ContractorRequest request) {
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        Contractor entity = new Contractor();
        entity.setCode(nextCode());
        apply(entity, request);
        Contractor saved = repository.save(entity);

        auditBuilderService.log(
                "contractor",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.CONTRACTOR,
                "Подрядчик создан" ,
                null,
                saved
        );

        return ContractorDto.from(saved);
    }

    @Transactional
    public ContractorDto update(UUID id, ContractorRequest request) {
        CodeGenerationUtils.rejectClientProvidedCode(request.code());
        Contractor entity = getOrThrow(id);

        apply(entity, request);
        Contractor save = repository.save(entity);

        auditBuilderService.log(
                "contractor",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.CONTRACTOR,
                "Подрядчик обновлен",
                entity,
                save
        );
        return ContractorDto.from(entity);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        Contractor saved = repository.save(entity);

        auditBuilderService.log(
                "contractor",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.CONTRACTOR,
                "Подрядчик удален",
                entity,
                null
        );

    }

    private ContractorDto.Summary summaryForList(UUID contractorId) {
        List<ContractorContract> contracts = contractorContractRepository.findAllByContractorIdAndIsDeletedFalse(contractorId);
        List<WorkOrder> workOrders = workOrderRepository.findAllByContractorIdAndIsDeletedFalseOrderByUpdatedAtDesc(contractorId);
        long activeContracts = contracts.stream().filter(this::isActiveContract).count();
        long activeWorkOrders = workOrders.stream().filter(this::isActiveWorkOrder).count();
        return new ContractorDto.Summary(activeContracts, activeWorkOrders, 0, 0);
    }

    private List<WorkOrder> loadDetailWorkOrders(UUID contractorId, List<ContractorWork> contractorWorks) {
        Map<UUID, WorkOrder> result = new LinkedHashMap<>();
        workOrderRepository.findAllByContractorIdAndIsDeletedFalseOrderByUpdatedAtDesc(contractorId)
                .forEach(workOrder -> {
                    if (workOrder.getId() != null) {
                        result.put(workOrder.getId(), workOrder);
                    }
                });

        List<UUID> missingLinkedWorkOrderIds = contractorWorks.stream()
                .map(ContractorWork::getWorkOrderId)
                .filter(Objects::nonNull)
                .filter(workOrderId -> !result.containsKey(workOrderId))
                .distinct()
                .toList();
        if (!missingLinkedWorkOrderIds.isEmpty()) {
            workOrderRepository.findAllByIdInAndIsDeletedFalse(missingLinkedWorkOrderIds)
                    .forEach(workOrder -> {
                        if (workOrder.getId() != null) {
                            result.put(workOrder.getId(), workOrder);
                        }
                    });
        }
        return new ArrayList<>(result.values());
    }

    private Map<UUID, CostCategory> loadCostCategories(List<ActualCost> actualCosts) {
        List<UUID> costCategoryIds = actualCosts.stream()
                .map(ActualCost::getCostCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (costCategoryIds.isEmpty()) {
            return Map.of();
        }
        return costCategoryRepository.findAllByIdInAndIsDeletedFalse(costCategoryIds).stream()
                .collect(Collectors.toMap(CostCategory::getId, Function.identity(), (left, ignored) -> left));
    }

    private Map<UUID, Equipment> loadEquipment(List<WorkOrder> workOrders) {
        List<UUID> equipmentIds = workOrders.stream()
                .map(WorkOrder::getEquipmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (equipmentIds.isEmpty()) {
            return Map.of();
        }
        return equipmentRepository.findAllByIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity(), (left, ignored) -> left));
    }

    private ContractorDetailDto.WorkOrderRef toWorkOrderRef(
            WorkOrder workOrder,
            Equipment equipment,
            List<ActualCost> actualCosts,
            Map<UUID, CostCategory> costCategories
    ) {
        return new ContractorDetailDto.WorkOrderRef(
                workOrder.getId(),
                workOrder.getNumber(),
                workOrder.getTitle(),
                workOrder.getStatus() == null ? null : workOrder.getStatus().name(),
                workOrder.getCreatedAt(),
                workOrder.getStartPlannedAt(),
                workOrder.getEndPlannedAt(),
                equipment == null ? null : new ContractorDetailDto.EquipmentRef(
                        equipment.getId(),
                        equipment.getCode(),
                        equipment.getName()
                ),
                actualCosts.stream().map(cost -> toActualCostRef(cost, costCategories)).toList(),
                List.of()
        );
    }

    private ContractorDetailDto.ContractorWorkRef toContractorWorkRef(
            ContractorWork work,
            WorkOrder workOrder,
            List<ActualCost> actualCosts,
            Map<UUID, CostCategory> costCategories
    ) {
        ContractorDetailDto.ContractorWorkOrderRef workOrderRef = workOrder == null
                ? null
                : new ContractorDetailDto.ContractorWorkOrderRef(
                workOrder.getId(),
                workOrder.getNumber(),
                workOrder.getTitle(),
                workOrder.getStatus() == null ? null : workOrder.getStatus().name()
        );
        return new ContractorDetailDto.ContractorWorkRef(
                work.getId(),
                work.getContractorId(),
                work.getWorkOrderId(),
                work.getDescription(),
                work.getStatus() == null ? null : work.getStatus().name(),
                work.getStartedAt(),
                work.getCompletedAt(),
                work.getCost(),
                work.getResult(),
                work.getAcceptanceComment(),
                work.getCreatedById(),
                work.getAcceptedById(),
                work.getAcceptedAt(),
                work.getCreatedAt(),
                null,
                null,
                workOrderRef,
                actualCosts.stream().map(cost -> toActualCostRef(cost, costCategories)).toList()
        );
    }

    private ContractorDetailDto.ActualCostRef toActualCostRef(
            ActualCost cost,
            Map<UUID, CostCategory> costCategories
    ) {
        CostCategory category = costCategories.get(cost.getCostCategoryId());
        ContractorDetailDto.CostCategoryRef categoryRef = category == null
                ? new ContractorDetailDto.CostCategoryRef(cost.getCostCategoryId(), "", "", null)
                : new ContractorDetailDto.CostCategoryRef(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription()
        );
        return new ContractorDetailDto.ActualCostRef(
                cost.getId(),
                cost.getAmount(),
                cost.getStatus() == null ? null : cost.getStatus().name(),
                cost.getCreatedAt(),
                cost.getCostDate(),
                cost.getNotes(),
                cost.getReviewedAt(),
                cost.getReviewComment(),
                cost.getSourceType() == null ? null : cost.getSourceType().name(),
                cost.getSourceId(),
                categoryRef,
                null
        );
    }

    private ContractorDetailDto.Summary detailSummary(
            List<ContractorContract> contracts,
            List<WorkOrder> workOrders,
            List<ContractorWork> contractorWorks,
            List<ActualCost> actualCosts
    ) {
        long activeContracts = contracts.stream().filter(this::isActiveContract).count();
        long activeWorkOrders = workOrders.stream().filter(this::isActiveWorkOrder).count();
        long pendingActualCostReview = actualCosts.stream()
                .filter(cost -> cost.getStatus() == ActualCostStatus.PENDING)
                .count();
        double totalContractAmount = contracts.stream()
                .map(ContractorContract::getAmount)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        double totalContractorWorkCost = contractorWorks.stream()
                .map(ContractorWork::getCost)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        double totalActualCost = actualCosts.stream().mapToDouble(ActualCost::getAmount).sum();
        double totalReflectedContractorCost = actualCosts.stream()
                .filter(cost -> cost.getStatus() == ActualCostStatus.APPROVED)
                .mapToDouble(ActualCost::getAmount)
                .sum();
        long completedWorks = contractorWorks.stream()
                .filter(work -> work.getStatus() == ContractorWorkStatus.COMPLETED)
                .count();
        long inProgressWorks = contractorWorks.stream()
                .filter(work -> work.getStatus() == ContractorWorkStatus.IN_PROGRESS)
                .count();
        long acceptedWorks = contractorWorks.stream()
                .filter(work -> work.getStatus() == ContractorWorkStatus.ACCEPTED)
                .count();
        long acceptedAwaitingReflection = contractorWorks.stream()
                .filter(work -> work.getStatus() == ContractorWorkStatus.ACCEPTED)
                .filter(work -> awaitingReflection(work, actualCosts))
                .count();
        return new ContractorDetailDto.Summary(
                activeContracts,
                activeWorkOrders,
                pendingActualCostReview,
                acceptedAwaitingReflection,
                totalContractAmount,
                totalContractorWorkCost,
                totalReflectedContractorCost,
                totalActualCost,
                0,
                completedWorks,
                inProgressWorks,
                completedWorks,
                acceptedWorks,
                0
        );
    }

    private boolean awaitingReflection(ContractorWork work, List<ActualCost> actualCosts) {
        double expected = work.getCost() == null ? 0 : work.getCost();
        if (expected <= 0) {
            return false;
        }
        double approved = actualCosts.stream()
                .filter(cost -> Objects.equals(cost.getContractorWorkId(), work.getId()))
                .filter(cost -> cost.getStatus() == ActualCostStatus.APPROVED)
                .mapToDouble(ActualCost::getAmount)
                .sum();
        return approved < expected;
    }

    private boolean isActiveContract(ContractorContract contract) {
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if (contract.getStartDate() != null && contract.getStartDate().isAfter(today)) {
            return false;
        }
        return contract.getEndDate() == null || !contract.getEndDate().isBefore(today);
    }

    private boolean isActiveWorkOrder(WorkOrder workOrder) {
        return workOrder.getStatus() != null && ACTIVE_WORK_ORDER_STATUSES.contains(workOrder.getStatus());
    }

    private Contractor getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Contractor not found: " + id));
    }

    private void apply(Contractor entity, ContractorRequest request) {
        entity.setName(request.name());
        entity.setTaxNumber(request.taxNumber());
        entity.setContactPerson(request.contactPerson());
        entity.setPhone(request.phone());
        entity.setEmail(request.email());
        entity.setSpecialization(request.specialization());
        entity.setDirectorName(trimToNull(request.directorName()));
        entity.setBankName(trimToNull(request.bankName()));
        entity.setBankAccount(trimToNull(request.bankAccount()));
        entity.setMfo(trimToNull(request.mfo()));
        if (request.status() != null) entity.setStatus(request.status());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nextCode() {
        String prefix = "CTR-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "CTR",
                () -> repository.maxSequenceByCodePrefix(prefix),
                repository::existsByCodeAndIsDeletedFalse
        );
    }

}
