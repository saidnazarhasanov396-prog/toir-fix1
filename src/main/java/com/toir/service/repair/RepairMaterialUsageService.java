package com.toir.service.repair;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.users.User;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WorkOrderSparePartRequirementStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.LowStockRecommendationService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepairMaterialUsageService {

    private static final Set<WorkOrderStatus> MATERIAL_ISSUE_ALLOWED_STATUSES = Set.of(
            WorkOrderStatus.APPROVED,
            WorkOrderStatus.IN_PROGRESS
    );

    private final RepairMaterialUsageRepository repository;
    private final WarehouseStockRepository stockRepository;
    private final WorkOrderRepository workOrderRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditBuilderService auditBuilderService;
    private final WarehouseRepository warehouseRepository;
    private final SparePartRepository sparePartRepository;
    private final UserRepository userRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final ScopeAccessService scopeAccessService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final ActualCostRepository actualCostRepository;
    private final RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;
    private final CostCategoryRepository costCategoryRepository;
    private final WorkOrderSparePartRequirementRepository requirementRepository;
    private final ToirStockService toirStockService;
    private final LegacyStockProjectionService legacyStockProjectionService;


    @Transactional(readOnly = true)
    public List<RepairMaterialUsageDto> findByWorkOrder(UUID workOrderId) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        return safeList(repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId)).stream()
                .filter(usage -> canAccessWarehouseId(usage.getWarehouseId()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), this::toDetailedDtos));
    }

    @Transactional(readOnly = true)
    public List<RepairMaterialUsageDto> findByRepairRequest(UUID repairRequestId) {
        repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)
                .orElseThrow(() -> RestException.notFound("Repair request not found: " + repairRequestId));
        List<WorkOrder> workOrders = safeList(workOrderRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .stream()
                .filter(workOrder -> {
                    try {
                        assertCanAccessWorkOrder(workOrder);
                        return true;
                    } catch (AccessDeniedException ex) {
                        return false;
                    }
                })
                .toList();
        return findByWorkOrders(workOrders);
    }

    @Transactional(readOnly = true)
    public List<RepairMaterialUsageDto> findByPprTask(UUID taskId) {
        return workOrderRepository.findFirstByPprTaskIdAndIsDeletedFalseOrderByUpdatedAtDesc(taskId)
                .map(workOrder -> {
                    assertCanAccessWorkOrder(workOrder);
                    return findByWorkOrders(List.of(workOrder));
                })
                .orElse(List.of());
    }

    @Transactional
    public RepairMaterialUsageDto register(UUID workOrderId, RepairMaterialUsageDto r) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        assertWorkOrderAllowsMaterialIssue(workOrder);
        equipmentStatusLifecycleService.assertOperationallyAllowed(workOrder.getEquipmentId(), "add material usage");
        assertCanAccessWarehouseId(r.warehouseId());
        if (r.quantity() <= 0) {
            throw RestException.badRequest("Quantity must be greater than 0");
        }

        WarehouseStock stock = stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(r.warehouseId(), r.sparePartId())
                .orElseThrow(() -> RestException.notFound("No stock found for spare part in this warehouse"));

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(r.warehouseId());
        movement.setSparePartId(r.sparePartId());
        movement.setWorkOrderId(workOrderId);
        movement.setType(StockMovementType.ISSUE);
        movement.setQuantity(r.quantity());
        movement.setUnitCost(r.unitCost());
        movement.setCreatedById(r.issuedById());
        movement.setNotes(r.notes());
        StockMovement savedMovement = stockMovementRepository.save(movement);
        postCoreStockIssue(workOrder, savedMovement);
        stock = legacyStockProjectionService.sync(r.warehouseId(), r.sparePartId());
        lowStockRecommendationService.evaluateStockSafely(stock);

        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setWorkOrderId(workOrderId);
        usage.setWarehouseId(r.warehouseId());
        usage.setSparePartId(r.sparePartId());
        usage.setStockMovementId(savedMovement.getId());
        usage.setIssuedAt(savedMovement.getOccurredAt());
        usage.setIssuedById(r.issuedById());
        usage.setQuantity(r.quantity());
        usage.setUnitCost(r.unitCost());
        usage.setNotes(r.notes());


        if (r.requirementId() != null) {
            WorkOrderSparePartRequirement requirement = requirementRepository
                    .findById(r.requirementId())
                    .filter(req -> !req.isDeleted() && req.getWorkOrderId().equals(workOrderId))
                    .orElseThrow(() -> RestException.notFound("Requirement not found: " + r.requirementId()));
            usage.setRequirementId(requirement.getId());


            if (!r.sparePartId().equals(requirement.getSparePartId())) {
                usage.setReplacedSparePartId(requirement.getSparePartId());
            }

            // Requirement statusini ISSUED ga o'tkazamiz
            requirement.setStatus(WorkOrderSparePartRequirementStatus.ISSUED);
            requirementRepository.save(requirement);
        }
        RepairMaterialUsage saved = repository.save(usage);
        syncMaterialActualCost(saved);

        auditBuilderService.log(
                "repair_material_usage",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.REPAIR_MATERIAL_USAGE,
                "Использование материала в ремонте создано",
                null,
                saved
        );

        return toDetailedDtos(List.of(saved)).getFirst();
    }

    private void postCoreStockIssue(WorkOrder workOrder, StockMovement movement) {
        toirStockService.postIssue(new StockIssueCommand(
                movement.getWarehouseId(),
                movement.getSparePartId(),
                null,
                BigDecimal.valueOf(movement.getQuantity()),
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                "WORK_ORDER",
                workOrder.getId(),
                null,
                movement.getNotes(),
                "work-order-material-issue:" + movement.getId()
        ));
    }

    private List<RepairMaterialUsageDto> findByWorkOrders(List<WorkOrder> workOrders) {
        if (workOrders.isEmpty()) {
            return List.of();
        }
        List<UUID> workOrderIds = workOrders.stream().map(WorkOrder::getId).toList();
        List<RepairMaterialUsage> usages = safeList(repository
                .findAllByWorkOrderIdInAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderIds))
                .stream()
                .filter(usage -> canAccessWarehouseId(usage.getWarehouseId()))
                .toList();
        return toDetailedDtos(usages, workOrders);
    }

    private List<RepairMaterialUsageDto> toDetailedDtos(List<RepairMaterialUsage> usages) {
        if (usages.isEmpty()) {
            return List.of();
        }
        List<UUID> workOrderIds = usages.stream()
                .map(RepairMaterialUsage::getWorkOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<WorkOrder> workOrders = workOrderIds.isEmpty()
                ? List.of()
                : safeList(workOrderRepository.findAllByIdInAndIsDeletedFalse(workOrderIds));
        return toDetailedDtos(usages, workOrders);
    }

    private List<RepairMaterialUsageDto> toDetailedDtos(List<RepairMaterialUsage> usages, List<WorkOrder> workOrders) {
        if (usages.isEmpty()) {
            return List.of();
        }
        Map<UUID, WorkOrder> workOrderById = workOrders.stream()
                .collect(Collectors.toMap(WorkOrder::getId, Function.identity(), (left, right) -> left));
        Map<UUID, Warehouse> warehouseById = safeList(warehouseRepository.findAllByIdInAndIsDeletedFalse(
                usages.stream().map(RepairMaterialUsage::getWarehouseId).filter(Objects::nonNull).distinct().toList())
        )
                .stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity(), (left, right) -> left));


        List<UUID> allSparePartIds = usages.stream()
                .flatMap(u -> java.util.stream.Stream.of(u.getSparePartId(), u.getReplacedSparePartId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, SparePart> sparePartById = safeList(sparePartRepository.findAllByIdInAndIsDeletedFalse(allSparePartIds))
                .stream()
                .collect(Collectors.toMap(SparePart::getId, Function.identity(), (left, right) -> left));

        List<UUID> issuedByIds = usages.stream()
                .map(RepairMaterialUsage::getIssuedById)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, User> userById = issuedByIds.isEmpty()
                ? Map.of()
                : safeList(userRepository.findAllByIdInAndIsDeletedFalse(issuedByIds)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        return usages.stream()
                .map(usage -> {
                    WorkOrder workOrder = workOrderById.get(usage.getWorkOrderId());
                    Warehouse warehouse = warehouseById.get(usage.getWarehouseId());
                    SparePart sparePart = sparePartById.get(usage.getSparePartId());
                    SparePart replacedSparePart = usage.getReplacedSparePartId() != null
                            ? sparePartById.get(usage.getReplacedSparePartId())
                            : null;
                    User issuedBy = usage.getIssuedById() == null ? null : userById.get(usage.getIssuedById());
                    return RepairMaterialUsageDto.detailed(
                            usage,
                            workOrder == null ? null : workOrder.getNumber(),
                            workOrder == null ? null : workOrder.getTitle(),
                            warehouse == null ? null : warehouse.getName(),
                            sparePart == null ? null : sparePart.getName(),
                            sparePart == null ? null : sparePart.getCode(),
                            sparePart == null ? null : sparePart.getKind(),
                            issuedBy == null ? null : issuedBy.getFullName(),
                            replacedSparePart == null ? null : replacedSparePart.getName(),
                            replacedSparePart == null ? null : replacedSparePart.getCode()
                    );
                })
                .toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private WorkOrder workOrderOrThrow(UUID workOrderId) {
        return workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
    }

    private void syncMaterialActualCost(RepairMaterialUsage usage) {
        if (usage.getUnitCost() == null || usage.getUnitCost() <= 0 || usage.getQuantity() <= 0 || usage.getId() == null) {
            return;
        }
        Optional<CostCategory> category = costCategoryRepository.findFirstByCodeAndIsDeletedFalse("MATERIALS");
        if (category.isEmpty()) {
            return;
        }
        ActualCost cost = actualCostRepository
                .findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                        ActualCostSourceType.MATERIAL_ISSUE,
                        usage.getId()
                )
                .orElseGet(ActualCost::new);
        cost.setSourceType(ActualCostSourceType.MATERIAL_ISSUE);
        cost.setSourceId(usage.getId());
        cost.setWorkOrderId(usage.getWorkOrderId());
        cost.setBudgetLineId(repairCampaignBudgetLineResolver.resolveForWorkOrderId(usage.getWorkOrderId()));
        cost.setCostCategoryId(category.get().getId());
        cost.setAmount(usage.getQuantity() * usage.getUnitCost());
        cost.setStatus(ActualCostStatus.PENDING);
        cost.setCostDate(usage.getIssuedAt() == null ? java.time.Instant.now() : usage.getIssuedAt());
        cost.setNotes("Generated from material issue %s".formatted(usage.getId()));
        actualCostRepository.save(cost);
    }

    private void assertWorkOrderAllowsMaterialIssue(WorkOrder workOrder) {
        if (!isMaterialIssueAllowedStatus(workOrder.getStatus())) {
            throw RestException.badRequest(
                    "Materials can be issued only for approved or in-progress work orders");
        }
    }

    private boolean isMaterialIssueAllowedStatus(WorkOrderStatus status) {
        return MATERIAL_ISSUE_ALLOWED_STATUSES.contains(status);
    }

    private void assertCanAccessWorkOrder(WorkOrder workOrder) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (workOrder.getDepartmentId() == null || !scopeAccessService.canAccessDepartment(workOrder.getDepartmentId())) {
            throw new AccessDeniedException("Access denied by work order department scope");
        }
    }

    private void assertCanAccessWarehouseId(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + warehouseId));
        if (!canAccessWarehouse(warehouse)) {
            throw new AccessDeniedException("Access denied by warehouse scope");
        }
    }

    private boolean canAccessWarehouseId(UUID warehouseId) {
        return warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .map(this::canAccessWarehouse)
                .orElse(false);
    }

    private boolean canAccessWarehouse(Warehouse warehouse) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return (warehouse.getDepartmentId() != null && scopeAccessService.canAccessDepartment(warehouse.getDepartmentId()))
                || (warehouse.getResponsibleId() != null && scopeAccessService.canAccessEmployee(warehouse.getResponsibleId()));
    }
}
