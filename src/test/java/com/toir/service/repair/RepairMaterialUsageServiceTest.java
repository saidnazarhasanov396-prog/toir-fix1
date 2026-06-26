package com.toir.service.repair;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.StockMovement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.StockMovementType;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairMaterialUsageServiceTest {

    @Mock
    RepairMaterialUsageRepository repository;

    @Mock
    private AuditBuilderService auditBuilderService;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    StockMovementRepository stockMovementRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    EquipmentStatusLifecycleService equipmentStatusLifecycleService;

    @Mock
    LowStockRecommendationService lowStockRecommendationService;

    @Mock
    ActualCostRepository actualCostRepository;

    @Mock
    CostCategoryRepository costCategoryRepository;

    @Mock
    RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;

    @Mock
    WorkOrderSparePartRequirementRepository requirementRepository;

    @Mock
    ToirStockService toirStockService;
    @Mock
    LegacyStockProjectionService legacyStockProjectionService;

    @InjectMocks
    RepairMaterialUsageService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "auditBuilderService", auditBuilderService);
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(warehouseRepository.findByIdAndIsDeletedFalse(any()))
                .thenAnswer(invocation -> Optional.of(warehouse(invocation.getArgument(0))));
    }

    @Test
    void registerFailsWhenWorkOrderNotFound() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(workOrderId, usageDto(5)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Work order not found");

        verifyNoInteractions(stockRepository, repository, stockMovementRepository);
    }

    @Test
    void registerFailsWhenRequestedQuantityIsGreaterThanAvailable() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(10);
        stock.setReservedQty(4);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });
        org.mockito.Mockito.doThrow(RestException.badRequest(
                        "Insufficient available stock: available=6, requested=7"))
                .when(toirStockService).postIssue(any(StockIssueCommand.class));

        assertThatThrownBy(() -> service.register(
                workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 7, 10.0)
                ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Insufficient available stock");

        verify(stockRepository, never()).save(any(WarehouseStock.class));
        verify(repository, never()).save(any(RepairMaterialUsage.class));
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void registerFailsForZeroOrNegativeQuantity() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));

        assertThatThrownBy(() -> service.register(workOrderId, usageDto(0)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        assertThatThrownBy(() -> service.register(workOrderId, usageDto(-1)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        verifyNoInteractions(stockRepository, repository, stockMovementRepository);
    }

    @Test
    void registerSucceedsWhenRequestedQuantityIsWithinAvailableAndCreatesIssueMovement() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(10);
        stock.setReservedQty(3);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setQuantity(3);
            return stock;
        });
        when(repository.save(any(RepairMaterialUsage.class)))
                .thenAnswer(invocation -> {
                    RepairMaterialUsage usage = invocation.getArgument(0);
                    ReflectionTestUtils.setField(usage, "id", UUID.randomUUID());
                    return usage;
                });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });

        RepairMaterialUsageDto result = service.register(
                workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 7, 12.5)
        );

        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(result.sparePartId()).isEqualTo(sparePartId);
        assertThat(result.quantity()).isEqualTo(7);
        assertThat(result.unitCost()).isEqualTo(12.5);
        assertThat(stock.getQuantity()).isEqualTo(3);
        assertThat(stock.getReservedQty()).isEqualTo(3);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.ISSUE);
        assertThat(movement.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(movement.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(movement.getSparePartId()).isEqualTo(sparePartId);
        assertThat(movement.getQuantity()).isEqualTo(7);
        assertThat(movement.getUnitCost()).isEqualTo(12.5);
        ArgumentCaptor<StockIssueCommand> coreIssueCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postIssue(coreIssueCaptor.capture());
        StockIssueCommand coreIssue = coreIssueCaptor.getValue();
        assertThat(coreIssue.warehouseId()).isEqualTo(warehouseId);
        assertThat(coreIssue.sparePartId()).isEqualTo(sparePartId);
        assertThat(coreIssue.quantity()).isEqualByComparingTo("7");
        assertThat(coreIssue.referenceType()).isEqualTo("WORK_ORDER");
        assertThat(coreIssue.referenceId()).isEqualTo(workOrderId);
        assertThat(coreIssue.idempotencyKey()).isEqualTo("work-order-material-issue:" + movement.getId());
        verify(lowStockRecommendationService).evaluateStockSafely(stock);
    }

    @Test
    void registerWithKnownUnitCostCreatesSourceLinkedPendingActualCost() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 0);
        CostCategory category = new CostCategory();
        category.setId(categoryId);
        category.setCode("MATERIALS");

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenReturn(stock);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });
        when(repository.save(any(RepairMaterialUsage.class))).thenAnswer(invocation -> {
            RepairMaterialUsage usage = invocation.getArgument(0);
            usage.setId(UUID.randomUUID());
            return usage;
        });
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("MATERIALS")).thenReturn(Optional.of(category));
        when(actualCostRepository.findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                any(), any())).thenReturn(Optional.empty());
        when(repairCampaignBudgetLineResolver.resolveForWorkOrderId(workOrderId)).thenReturn(budgetLineId);

        RepairMaterialUsageDto result = service.register(
                workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 2, 15.0)
        );

        assertThat(result.costWarning()).isNull();
        ArgumentCaptor<ActualCost> costCaptor = ArgumentCaptor.forClass(ActualCost.class);
        verify(actualCostRepository).save(costCaptor.capture());
        ActualCost cost = costCaptor.getValue();
        assertThat(cost.getSourceType()).isEqualTo(ActualCostSourceType.MATERIAL_ISSUE);
        assertThat(cost.getSourceId()).isEqualTo(result.id());
        assertThat(cost.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(cost.getBudgetLineId()).isEqualTo(budgetLineId);
        assertThat(cost.getCostCategoryId()).isEqualTo(categoryId);
        assertThat(cost.getStatus()).isEqualTo(ActualCostStatus.PENDING);
        assertThat(cost.getAmount()).isEqualTo(30.0);
    }

    @Test
    void registerWithKnownUnitCostUpdatesExistingMaterialIssueActualCostWithoutDuplicate() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();
        UUID existingCostId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 0);
        CostCategory category = new CostCategory();
        category.setId(categoryId);
        category.setCode("MATERIALS");
        ActualCost existingCost = new ActualCost();
        existingCost.setId(existingCostId);
        existingCost.setSourceType(ActualCostSourceType.MATERIAL_ISSUE);
        existingCost.setSourceId(usageId);
        existingCost.setAmount(10.0);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenReturn(stock);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });
        when(repository.save(any(RepairMaterialUsage.class))).thenAnswer(invocation -> {
            RepairMaterialUsage usage = invocation.getArgument(0);
            usage.setId(usageId);
            return usage;
        });
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("MATERIALS")).thenReturn(Optional.of(category));
        when(actualCostRepository.findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                ActualCostSourceType.MATERIAL_ISSUE, usageId)).thenReturn(Optional.of(existingCost));

        service.register(
                workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 3, 20.0)
        );

        ArgumentCaptor<ActualCost> costCaptor = ArgumentCaptor.forClass(ActualCost.class);
        verify(actualCostRepository).save(costCaptor.capture());
        ActualCost cost = costCaptor.getValue();
        assertThat(cost.getId()).isEqualTo(existingCostId);
        assertThat(cost.getSourceType()).isEqualTo(ActualCostSourceType.MATERIAL_ISSUE);
        assertThat(cost.getSourceId()).isEqualTo(usageId);
        assertThat(cost.getAmount()).isEqualTo(60.0);
    }

    @Test
    void registerWithoutUnitCostDoesNotCreateFakeActualCostAndReturnsWarning() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 0);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenReturn(stock);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });
        when(repository.save(any(RepairMaterialUsage.class))).thenAnswer(invocation -> {
            RepairMaterialUsage usage = invocation.getArgument(0);
            usage.setId(UUID.randomUUID());
            return usage;
        });

        RepairMaterialUsageDto result = service.register(
                workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 2, null)
        );

        assertThat(result.costWarning()).contains("unit cost");
        verifyNoInteractions(actualCostRepository, costCategoryRepository);
    }

    @Test
    void findByRepairRequestReturnsMaterialUsageFromLinkedWorkOrders() {
        UUID repairRequestId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        RepairMaterialUsage usage = usage(workOrderId, warehouseId);
        RepairRequest repairRequest = new RepairRequest();
        repairRequest.setId(repairRequestId);
        WorkOrder workOrder = workOrder(workOrderId, WorkOrderStatus.IN_PROGRESS);
        workOrder.setNumber("WO-7");
        workOrder.setTitle("Pump repair");

        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest));
        when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(workOrder));
        when(repository.findAllByWorkOrderIdInAndIsDeletedFalseOrderByUpdatedAtDesc(java.util.List.of(workOrderId)))
                .thenReturn(java.util.List.of(usage));

        var result = service.findByRepairRequest(repairRequestId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().workOrderId()).isEqualTo(workOrderId);
        assertThat(result.getFirst().workOrderNumber()).isEqualTo("WO-7");
        assertThat(result.getFirst().workOrderTitle()).isEqualTo("Pump repair");
        assertThat(result.getFirst().warehouseId()).isEqualTo(warehouseId);
    }

    @Test
    void findByPprTaskReturnsEmptyListWhenNoWorkOrderExists() {
        UUID taskId = UUID.randomUUID();
        when(workOrderRepository.findFirstByPprTaskIdAndIsDeletedFalseOrderByUpdatedAtDesc(taskId))
                .thenReturn(Optional.empty());

        assertThat(service.findByPprTask(taskId)).isEmpty();
    }

    @Test
    void findByPprTaskReturnsMaterialUsageFromLinkedWorkOrder() {
        UUID taskId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, WorkOrderStatus.IN_PROGRESS);
        workOrder.setPprTaskId(taskId);
        workOrder.setNumber("PPR-WO-1");
        RepairMaterialUsage usage = usage(workOrderId, warehouseId);

        when(workOrderRepository.findFirstByPprTaskIdAndIsDeletedFalseOrderByUpdatedAtDesc(taskId))
                .thenReturn(Optional.of(workOrder));
        when(repository.findAllByWorkOrderIdInAndIsDeletedFalseOrderByUpdatedAtDesc(java.util.List.of(workOrderId)))
                .thenReturn(java.util.List.of(usage));

        var result = service.findByPprTask(taskId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().workOrderId()).isEqualTo(workOrderId);
        assertThat(result.getFirst().workOrderNumber()).isEqualTo("PPR-WO-1");
    }

    @Test
    void registerSucceedsForInProgressWorkOrder() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 0);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.IN_PROGRESS)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setQuantity(7);
            return stock;
        });
        when(repository.save(any(RepairMaterialUsage.class)))
                .thenAnswer(invocation -> {
                    RepairMaterialUsage usage = invocation.getArgument(0);
                    ReflectionTestUtils.setField(usage, "id", UUID.randomUUID());
                    return usage;
                });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RepairMaterialUsageDto result = service.register(
                workOrderId,
                new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 3, null)
        );

        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @Test
    void registerFailsForDraftWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.DRAFT);
    }

    @Test
    void registerFailsForPlannedWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.PLANNED);
    }

    @Test
    void registerFailsForSuspendedWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.SUSPENDED);
    }

    @Test
    void registerFailsForCompletedWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.COMPLETED);
    }

    @Test
    void registerFailsForClosedWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.CLOSED);
    }

    @Test
    void registerFailsForCancelledWorkOrder() {
        assertRegisterBlockedForStatus(WorkOrderStatus.CANCELLED);
    }

    // ═══════════════════════════════════════════════════════
    // Task 5 — Requirement bog'lash testlari
    // ═══════════════════════════════════════════════════════

    @Test
    void registerWithRequirementId_linksRequirementToUsage() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();

        mockSuccessfulRegister(workOrderId, warehouseId, sparePartId);
        when(requirementRepository.findById(requirementId))
                .thenReturn(Optional.of(requirement(requirementId, workOrderId, sparePartId)));

        service.register(workOrderId, usageDtoWithRequirement(warehouseId, sparePartId, 2, requirementId));

        ArgumentCaptor<RepairMaterialUsage> captor = ArgumentCaptor.forClass(RepairMaterialUsage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRequirementId()).isEqualTo(requirementId);
    }

    @Test
    void registerWithRequirementId_setsRequirementStatusToIssued() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();

        mockSuccessfulRegister(workOrderId, warehouseId, sparePartId);
        when(requirementRepository.findById(requirementId))
                .thenReturn(Optional.of(requirement(requirementId, workOrderId, sparePartId)));

        service.register(workOrderId, usageDtoWithRequirement(warehouseId, sparePartId, 2, requirementId));

        ArgumentCaptor<WorkOrderSparePartRequirement> captor =
                ArgumentCaptor.forClass(WorkOrderSparePartRequirement.class);
        verify(requirementRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkOrderSparePartRequirementStatus.ISSUED);
    }

    @Test
    void registerWithDifferentSparePart_setsReplacedSparePartId() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID plannedSparePartId = UUID.randomUUID();
        UUID actualSparePartId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();

        mockSuccessfulRegister(workOrderId, warehouseId, actualSparePartId);
        when(requirementRepository.findById(requirementId))
                .thenReturn(Optional.of(requirement(requirementId, workOrderId, plannedSparePartId)));

        service.register(workOrderId, usageDtoWithRequirement(warehouseId, actualSparePartId, 2, requirementId));

        ArgumentCaptor<RepairMaterialUsage> captor = ArgumentCaptor.forClass(RepairMaterialUsage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReplacedSparePartId()).isEqualTo(plannedSparePartId);
    }

    @Test
    void registerWithSameSparePart_doesNotSetReplacedSparePartId() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();

        mockSuccessfulRegister(workOrderId, warehouseId, sparePartId);
        when(requirementRepository.findById(requirementId))
                .thenReturn(Optional.of(requirement(requirementId, workOrderId, sparePartId)));

        service.register(workOrderId, usageDtoWithRequirement(warehouseId, sparePartId, 2, requirementId));

        ArgumentCaptor<RepairMaterialUsage> captor = ArgumentCaptor.forClass(RepairMaterialUsage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReplacedSparePartId()).isNull();
    }

    @Test
    void registerWithRequirementId_requirementNotFound_throws404() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();

        mockSuccessfulRegister(workOrderId, warehouseId, sparePartId);
        when(requirementRepository.findById(requirementId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(
                workOrderId, usageDtoWithRequirement(warehouseId, sparePartId, 2, requirementId)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Requirement not found");

        verify(repository, never()).save(any(RepairMaterialUsage.class));
    }

    @Test
    void registerWithRequirementId_requirementBelongsToDifferentWorkOrder_throws404() {
        UUID workOrderId = UUID.randomUUID();
        UUID otherWorkOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();

        mockSuccessfulRegister(workOrderId, warehouseId, sparePartId);
        when(requirementRepository.findById(requirementId))
                .thenReturn(Optional.of(requirement(requirementId, otherWorkOrderId, sparePartId)));

        assertThatThrownBy(() -> service.register(
                workOrderId, usageDtoWithRequirement(warehouseId, sparePartId, 2, requirementId)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Requirement not found");

        verify(repository, never()).save(any(RepairMaterialUsage.class));
    }

    @Test
    void registerWithoutRequirementId_doesNotTouchRequirementRepository() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        mockSuccessfulRegister(workOrderId, warehouseId, sparePartId);

        service.register(workOrderId, new RepairMaterialUsageDto(null, null, warehouseId, sparePartId, 2, 10.0));

        verifyNoInteractions(requirementRepository);
    }

    // ═══════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════

    private void assertRegisterBlockedForStatus(WorkOrderStatus status) {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, status)));

        assertThatThrownBy(() -> service.register(workOrderId, usageDto(1)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Materials can be issued only for approved or in-progress work orders");

        verifyNoInteractions(stockRepository, repository, stockMovementRepository);
    }

    private void mockSuccessfulRegister(UUID workOrderId, UUID warehouseId, UUID sparePartId) {
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 0);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));
        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        lenient().when(stockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(stockMovementRepository.save(any())).thenAnswer(i -> {
            StockMovement m = i.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        lenient().when(repository.save(any())).thenAnswer(i -> {
            RepairMaterialUsage u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
    }

    private RepairMaterialUsageDto usageDto(double quantity) {
        return new RepairMaterialUsageDto(
                null, null, UUID.randomUUID(), UUID.randomUUID(), quantity, 10.0
        );
    }

    private RepairMaterialUsageDto usageDtoWithRequirement(
            UUID warehouseId, UUID sparePartId, double quantity, UUID requirementId) {
        return new RepairMaterialUsageDto(
                null, null, null, null,
                warehouseId, null,
                sparePartId, null, null, null,
                quantity, 10.0, null, null, null, null, null, null, null,
                requirementId, null, null, null
        );
    }

    private WorkOrder workOrder(UUID workOrderId, WorkOrderStatus status) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setStatus(status);
        return workOrder;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity, double reservedQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        return stock;
    }

    private RepairMaterialUsage usage(UUID workOrderId, UUID warehouseId) {
        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(UUID.randomUUID());
        usage.setWorkOrderId(workOrderId);
        usage.setWarehouseId(warehouseId);
        usage.setSparePartId(UUID.randomUUID());
        usage.setQuantity(2);
        usage.setUnitCost(4.5);
        return usage;
    }

    private Warehouse warehouse(UUID warehouseId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setActive(true);
        return warehouse;
    }

    private WorkOrderSparePartRequirement requirement(UUID id, UUID workOrderId, UUID sparePartId) {
        WorkOrderSparePartRequirement req = new WorkOrderSparePartRequirement();
        req.setId(id);
        req.setWorkOrderId(workOrderId);
        req.setSparePartId(sparePartId);
        req.setStatus(WorkOrderSparePartRequirementStatus.PLANNED);
        return req;
    }
}
