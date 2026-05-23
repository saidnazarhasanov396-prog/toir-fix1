package com.toir.service.repair;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.entity.StockMovement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.StockMovementType;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private final ScopeAccessService scopeAccessService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;


    @Transactional(readOnly = true)
    public List<RepairMaterialUsageDto> findByWorkOrder(UUID workOrderId) {
        WorkOrder workOrder = workOrderOrThrow(workOrderId);
        assertCanAccessWorkOrder(workOrder);
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId).stream()
                .filter(usage -> canAccessWarehouseId(usage.getWarehouseId()))
                .map(RepairMaterialUsageDto::from)
                .toList();
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
        double available = stock.getAvailable();
        if (r.quantity() > available) {
            throw RestException.badRequest("Cannot write off more than available: available="
                    + available + ", requested=" + r.quantity());
        }
        stock.setQuantity(stock.getQuantity() - r.quantity());
        stockRepository.save(stock);

        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setWorkOrderId(workOrderId);
        usage.setWarehouseId(r.warehouseId());
        usage.setSparePartId(r.sparePartId());
        usage.setQuantity(r.quantity());
        usage.setUnitCost(r.unitCost());
        RepairMaterialUsage saved = repository.save(usage);

        StockMovement movement = new StockMovement();
        movement.setWarehouseId(r.warehouseId());
        movement.setSparePartId(r.sparePartId());
        movement.setWorkOrderId(workOrderId);
        movement.setType(StockMovementType.ISSUE);
        movement.setQuantity(r.quantity());
        movement.setUnitCost(r.unitCost());
        stockMovementRepository.save(movement);

        auditBuilderService.log(
                "repair_material_usage",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.REPAIR_MATERIAL_USAGE,
                "Использование материала в ремонте создано",
                null,
                saved
        );

        return RepairMaterialUsageDto.from(saved);
    }

    private WorkOrder workOrderOrThrow(UUID workOrderId) {
        return workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
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
