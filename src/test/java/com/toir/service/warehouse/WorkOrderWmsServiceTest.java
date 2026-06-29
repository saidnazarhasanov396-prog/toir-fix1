package com.toir.service.warehouse;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.reservation.ReservationRequest;
import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.dto.warehouse.WarehouseTaskLineDto;
import com.toir.dto.warehouse.WarehouseTaskRequest;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.workorder.WorkOrderMaterialReturnDto;
import com.toir.dto.workorder.WorkOrderMaterialReturnRequest;
import com.toir.dto.workorder.WorkOrderPickConfirmLineRequest;
import com.toir.dto.workorder.WorkOrderPickConfirmRequest;
import com.toir.dto.workorder.WorkOrderPickListRequest;
import com.toir.dto.workorder.WorkOrderWmsReservationLineRequest;
import com.toir.dto.workorder.WorkOrderWmsReservationRequest;
import com.toir.dto.wms.WmsDocumentGroupRequest;
import com.toir.entity.InventoryTransaction;
import com.toir.entity.Reservation;
import com.toir.entity.StockMovement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.RepairMaterialReturn;
import com.toir.entity.warehouse.WarehouseStockLedger;
import com.toir.entity.warehouse.WarehouseTask;
import com.toir.entity.warehouse.WarehouseTaskLine;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.InventoryTransactionType;
import com.toir.enums.RepairMaterialReturnStatus;
import com.toir.enums.ReservationStatus;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskLineStatus;
import com.toir.enums.WarehouseTaskPriority;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.enums.WmsDocumentOperationType;
import com.toir.enums.WorkOrderSparePartRequirementStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.InventoryTransactionRepository;
import com.toir.repository.RepairMaterialReturnRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.service.ReservationService;
import com.toir.service.repair.RepairCampaignBudgetLineResolver;
import com.toir.repository.CostCategoryRepository;
import com.toir.service.LowStockRecommendationService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderWmsServiceTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock WorkOrderSparePartRequirementRepository requirementRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock ReservationService reservationService;
    @Mock WarehouseTaskService taskService;
    @Mock WarehouseTaskRepository taskRepository;
    @Mock ToirStockService toirStockService;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock RepairMaterialUsageRepository materialUsageRepository;
    @Mock RepairMaterialReturnRepository materialReturnRepository;
    @Mock InventoryTransactionRepository inventoryTransactionRepository;
    @Mock ActualCostRepository actualCostRepository;
    @Mock CostCategoryRepository costCategoryRepository;
    @Mock RepairCampaignBudgetLineResolver budgetLineResolver;
    @Mock LegacyStockProjectionService legacyStockProjectionService;
    @Mock LowStockRecommendationService lowStockRecommendationService;
    @Mock WmsDocumentPolicyService documentPolicyService;
    @Mock WmsStockCoordinateValidator coordinateValidator;
    @Mock AuditBuilderService auditBuilderService;

    WorkOrderWmsService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderWmsService(
                workOrderRepository,
                requirementRepository,
                reservationRepository,
                reservationService,
                taskService,
                taskRepository,
                toirStockService,
                stockMovementRepository,
                materialUsageRepository,
                materialReturnRepository,
                inventoryTransactionRepository,
                actualCostRepository,
                costCategoryRepository,
                budgetLineResolver,
                legacyStockProjectionService,
                lowStockRecommendationService,
                documentPolicyService,
                coordinateValidator,
                auditBuilderService
        );
    }

    @Test
    void reserveRequirementFromSpecificIdentityUpdatesRequirementStatus() {
        UUID workOrderId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID reservedById = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.of(2027, 3, 15);

        WorkOrder workOrder = workOrder(workOrderId);
        WorkOrderSparePartRequirement requirement = requirement(requirementId, workOrderId, sparePartId, 10);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(requirementRepository.findById(requirementId)).thenReturn(Optional.of(requirement));
        when(reservationRepository.findAllByWorkOrderIdAndStatusAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId, ReservationStatus.ACTIVE))
                .thenReturn(List.of());
        when(reservationService.reserve(any(ReservationRequest.class))).thenReturn(new ReservationDto(
                UUID.randomUUID(),
                null,
                warehouseId,
                sparePartId,
                binId,
                workOrderId,
                null,
                reservedById,
                requirementId,
                "LOT-7",
                "SN-8",
                expiryDate,
                WarehouseStockStatus.AVAILABLE,
                4,
                ReservationStatus.ACTIVE
        ));

        List<ReservationDto> result = service.reserve(workOrderId, new WorkOrderWmsReservationRequest(
                List.of(new WorkOrderWmsReservationLineRequest(
                        requirementId,
                        warehouseId,
                        sparePartId,
                        binId,
                        "LOT-7",
                        "SN-8",
                        expiryDate,
                        WarehouseStockStatus.AVAILABLE,
                        new BigDecimal("4.0000")
                )),
                reservedById,
                "RSV-1"
        ));

        assertThat(result).hasSize(1);
        assertThat(requirement.getStatus()).isEqualTo(WorkOrderSparePartRequirementStatus.PARTIALLY_RESERVED);

        ArgumentCaptor<ReservationRequest> reservationCaptor = ArgumentCaptor.forClass(ReservationRequest.class);
        verify(reservationService).reserve(reservationCaptor.capture());
        ReservationRequest reservation = reservationCaptor.getValue();
        assertThat(reservation.warehouseStockId()).isNull();
        assertThat(reservation.requirementId()).isEqualTo(requirementId);
        assertThat(reservation.warehouseId()).isEqualTo(warehouseId);
        assertThat(reservation.sparePartId()).isEqualTo(sparePartId);
        assertThat(reservation.binId()).isEqualTo(binId);
        assertThat(reservation.lotNumber()).isEqualTo("LOT-7");
        assertThat(reservation.serialNumber()).isEqualTo("SN-8");
        assertThat(reservation.expiryDate()).isEqualTo(expiryDate);
        assertThat(reservation.stockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);
    }

    @Test
    void pickListCreatesPickWarehouseTaskFromActiveReservations() {
        UUID workOrderId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId)));
        when(reservationRepository.findAllByWorkOrderIdAndStatusAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId, ReservationStatus.ACTIVE))
                .thenReturn(List.of(reservation(reservationId, requirementId, workOrderId, warehouseId, sparePartId, binId, 4)));
        when(taskService.create(any(WarehouseTaskRequest.class))).thenReturn(taskDto(taskId, warehouseId, sparePartId, binId));

        WarehouseTaskDto result = service.createPickList(workOrderId, new WorkOrderPickListRequest(UUID.randomUUID(), "pick"));

        assertThat(result.id()).isEqualTo(taskId);
        ArgumentCaptor<WarehouseTaskRequest> taskCaptor = ArgumentCaptor.forClass(WarehouseTaskRequest.class);
        verify(taskService).create(taskCaptor.capture());
        WarehouseTaskRequest task = taskCaptor.getValue();
        assertThat(task.taskType()).isEqualTo(WarehouseTaskType.PICK);
        assertThat(task.sourceType()).isEqualTo(WarehouseTaskSourceType.WORK_ORDER);
        assertThat(task.sourceId()).isEqualTo(workOrderId);
        assertThat(task.warehouseId()).isEqualTo(warehouseId);
        assertThat(task.lines().getFirst().fromBinId()).isEqualTo(binId);
        assertThat(task.lines().getFirst().plannedQty()).isEqualByComparingTo("4");
    }

    @Test
    void confirmPickFulfillsReservationCreatesIssueUsageAndActualCost() {
        UUID workOrderId = UUID.randomUUID();
        UUID pickListId = UUID.randomUUID();
        UUID taskLineId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();
        UUID issuedById = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.of(2027, 3, 15);
        List<WmsDocumentGroupRequest> documents = List.of(new WmsDocumentGroupRequest("Act", "ISSUE_ACT", "ISS-1", null, null));

        Reservation reservation = reservation(reservationId, requirementId, workOrderId, warehouseId, sparePartId, binId, 4);
        WorkOrderSparePartRequirement requirement = requirement(requirementId, workOrderId, sparePartId, 10);
        WarehouseTask task = pickTask(pickListId, taskLineId, workOrderId, warehouseId, sparePartId, binId);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId)));
        when(taskRepository.findByIdAndIsDeletedFalse(pickListId)).thenReturn(Optional.of(task));
        when(reservationRepository.findByIdAndIsDeletedFalse(reservationId)).thenReturn(Optional.of(reservation));
        when(requirementRepository.findById(requirementId)).thenReturn(Optional.of(requirement));
        when(materialUsageRepository.findAllByRequirementIdInAndIsDeletedFalse(List.of(requirementId))).thenReturn(List.of());
        when(toirStockService.fulfillReservation(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    WarehouseStockLedger ledger = new WarehouseStockLedger();
                    ledger.setUnitCost(new BigDecimal("12.00"));
                    return ledger;
                });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(movementId);
            return movement;
        });
        when(materialUsageRepository.save(any(RepairMaterialUsage.class))).thenAnswer(invocation -> {
            RepairMaterialUsage usage = invocation.getArgument(0);
            usage.setId(usageId);
            usage.setCreatedAt(Instant.now());
            return usage;
        });
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("MATERIALS")).thenReturn(Optional.of(costCategory()));
        when(actualCostRepository.save(any(ActualCost.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(WarehouseTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requirementRepository.save(any(WorkOrderSparePartRequirement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<RepairMaterialUsageDto> result = service.confirmPick(workOrderId, pickListId, new WorkOrderPickConfirmRequest(
                List.of(new WorkOrderPickConfirmLineRequest(
                        requirementId,
                        reservationId,
                        warehouseId,
                        binId,
                        sparePartId,
                        "LOT-7",
                        "SN-8",
                        expiryDate,
                        WarehouseStockStatus.AVAILABLE,
                        new BigDecimal("4.0000")
                )),
                "ISS-1",
                issuedById,
                UUID.randomUUID(),
                documents,
                true
        ));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(usageId);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(requirement.getStatus()).isEqualTo(WorkOrderSparePartRequirementStatus.PARTIALLY_ISSUED);
        assertThat(task.getStatus()).isEqualTo(WarehouseTaskStatus.DONE);
        assertThat(task.getLines().getFirst().getStatus()).isEqualTo(WarehouseTaskLineStatus.DONE);

        verify(documentPolicyService).validateReceiptDocuments(
                eq(WmsDocumentOperationType.WORK_ORDER_ISSUE),
                eq(documents),
                eq(false),
                eq(false),
                eq(true)
        );
        verify(toirStockService).fulfillReservation(
                warehouseId,
                sparePartId,
                binId,
                "LOT-7",
                "SN-8",
                expiryDate,
                WarehouseStockStatus.AVAILABLE,
                new BigDecimal("4.0000"),
                "WORK_ORDER_PICK",
                reservationId,
                "ISS-1",
                "work-order-pick:" + reservationId
        );

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.ISSUE);
        assertThat(movement.getSourceType()).isEqualTo(StockMovementSourceType.WORK_ORDER_MATERIAL_USAGE);
        assertThat(movement.getBinId()).isEqualTo(binId);
        assertThat(movement.getLotNumber()).isEqualTo("LOT-7");
        assertThat(movement.getSerialNumber()).isEqualTo("SN-8");
        assertThat(movement.getExpiryDate()).isEqualTo(expiryDate);

        ArgumentCaptor<RepairMaterialUsage> usageCaptor = ArgumentCaptor.forClass(RepairMaterialUsage.class);
        verify(materialUsageRepository).save(usageCaptor.capture());
        RepairMaterialUsage usage = usageCaptor.getValue();
        assertThat(usage.getRequirementId()).isEqualTo(requirementId);
        assertThat(usage.getStockMovementId()).isEqualTo(movementId);
        assertThat(usage.getBinId()).isEqualTo(binId);
        assertThat(usage.getLotNumber()).isEqualTo("LOT-7");
        assertThat(usage.getSerialNumber()).isEqualTo("SN-8");
        assertThat(usage.getExpiryDate()).isEqualTo(expiryDate);
        verify(actualCostRepository).save(any(ActualCost.class));
    }

    @Test
    void duplicateConfirmForFulfilledReservationReturnsExistingUsageWithoutPostingAgain() {
        UUID workOrderId = UUID.randomUUID();
        UUID pickListId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        RepairMaterialUsage existing = new RepairMaterialUsage();
        existing.setId(UUID.randomUUID());
        existing.setWorkOrderId(workOrderId);
        existing.setRequirementId(requirementId);
        existing.setWarehouseId(warehouseId);
        existing.setSparePartId(sparePartId);
        existing.setQuantity(4);

        Reservation reservation = reservation(reservationId, requirementId, workOrderId, warehouseId, sparePartId, binId, 4);
        reservation.setStatus(ReservationStatus.FULFILLED);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId)));
        when(taskRepository.findByIdAndIsDeletedFalse(pickListId)).thenReturn(Optional.of(pickTask(pickListId, UUID.randomUUID(), workOrderId, warehouseId, sparePartId, binId)));
        when(reservationRepository.findByIdAndIsDeletedFalse(reservationId)).thenReturn(Optional.of(reservation));
        when(materialUsageRepository.findAllByRequirementIdInAndIsDeletedFalse(List.of(requirementId))).thenReturn(List.of(existing));

        List<RepairMaterialUsageDto> result = service.confirmPick(workOrderId, pickListId, new WorkOrderPickConfirmRequest(
                List.of(new WorkOrderPickConfirmLineRequest(
                        requirementId,
                        reservationId,
                        warehouseId,
                        binId,
                        sparePartId,
                        null,
                        null,
                        null,
                        WarehouseStockStatus.AVAILABLE,
                        new BigDecimal("4.0000")
                )),
                "ISS-1",
                UUID.randomUUID(),
                null,
                List.of(),
                false
        ));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(existing.getId());
        verify(toirStockService, never()).fulfillReservation(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(stockMovementRepository, never()).save(any());
        verify(materialUsageRepository, never()).save(any());
    }

    @Test
    void returnUnusedNewPartPostsAvailableStockMovementInventoryTransactionAndReturnRow() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID returnedById = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.of(2028, 1, 31);
        List<WmsDocumentGroupRequest> documents = List.of(new WmsDocumentGroupRequest(
                "Return act",
                "MATERIAL_RETURN_ACT",
                "RET-1",
                null,
                UUID.randomUUID()
        ));

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId)));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(movementId);
            return movement;
        });
        when(toirStockService.postIncrease(any(StockReceiptCommand.class), eq(StockLedgerMovementType.RETURN)))
                .thenAnswer(invocation -> {
                    WarehouseStockLedger ledger = new WarehouseStockLedger();
                    ledger.setUnitCost(new BigDecimal("15.00"));
                    return ledger;
                });
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> {
            InventoryTransaction transaction = invocation.getArgument(0);
            transaction.setId(transactionId);
            return transaction;
        });
        when(materialReturnRepository.save(any(RepairMaterialReturn.class))).thenAnswer(invocation -> {
            RepairMaterialReturn materialReturn = invocation.getArgument(0);
            materialReturn.setId(returnId);
            return materialReturn;
        });

        WorkOrderMaterialReturnDto result = service.returnMaterial(workOrderId, new WorkOrderMaterialReturnRequest(
                null,
                warehouseId,
                sparePartId,
                binId,
                "LOT-R1",
                "SN-R1",
                expiryDate,
                WarehouseStockStatus.AVAILABLE,
                new BigDecimal("2.0000"),
                "Unused sealed part",
                returnedById,
                responsibleId,
                "RET-1",
                documents,
                true
        ));

        assertThat(result.id()).isEqualTo(returnId);
        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(result.materialUsageId()).isNull();
        assertThat(result.stockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);
        assertThat(result.status()).isEqualTo(RepairMaterialReturnStatus.POSTED);

        verify(documentPolicyService).validateReturnDocuments(
                "Unused sealed part",
                WarehouseStockStatus.AVAILABLE,
                documents,
                true
        );
        verify(coordinateValidator).assertCanReceiveOrMoveInto(warehouseId, binId, WarehouseStockStatus.AVAILABLE);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.RETURN);
        assertThat(movement.getSourceType()).isEqualTo(StockMovementSourceType.REPAIR_MATERIAL_RETURN);
        assertThat(movement.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(movement.getSparePartId()).isEqualTo(sparePartId);
        assertThat(movement.getBinId()).isEqualTo(binId);
        assertThat(movement.getLotNumber()).isEqualTo("LOT-R1");
        assertThat(movement.getSerialNumber()).isEqualTo("SN-R1");
        assertThat(movement.getExpiryDate()).isEqualTo(expiryDate);
        assertThat(movement.getStockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);
        assertThat(movement.getQuantity()).isEqualTo(2.0);

        ArgumentCaptor<StockReceiptCommand> receiptCaptor = ArgumentCaptor.forClass(StockReceiptCommand.class);
        verify(toirStockService).postIncrease(receiptCaptor.capture(), eq(StockLedgerMovementType.RETURN));
        StockReceiptCommand receipt = receiptCaptor.getValue();
        assertThat(receipt.warehouseId()).isEqualTo(warehouseId);
        assertThat(receipt.sparePartId()).isEqualTo(sparePartId);
        assertThat(receipt.binId()).isEqualTo(binId);
        assertThat(receipt.quantity()).isEqualByComparingTo("2.0000");
        assertThat(receipt.stockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);
        assertThat(receipt.referenceType()).isEqualTo("REPAIR_MATERIAL_RETURN");
        assertThat(receipt.referenceId()).isEqualTo(movementId);
        assertThat(receipt.referenceDocNo()).isEqualTo("RET-1");

        ArgumentCaptor<InventoryTransaction> transactionCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryTransactionRepository).save(transactionCaptor.capture());
        InventoryTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getType()).isEqualTo(InventoryTransactionType.RETURN);
        assertThat(transaction.getSourceType()).isEqualTo("REPAIR_MATERIAL_RETURN");
        assertThat(transaction.getSourceId()).isEqualTo(movementId);
        assertThat(transaction.getStockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);

        ArgumentCaptor<RepairMaterialReturn> returnCaptor = ArgumentCaptor.forClass(RepairMaterialReturn.class);
        verify(materialReturnRepository).save(returnCaptor.capture());
        RepairMaterialReturn materialReturn = returnCaptor.getValue();
        assertThat(materialReturn.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(materialReturn.getMaterialUsageId()).isNull();
        assertThat(materialReturn.getStockMovementId()).isEqualTo(movementId);
        assertThat(materialReturn.getInventoryTransactionId()).isEqualTo(transactionId);
        assertThat(materialReturn.getStatus()).isEqualTo(RepairMaterialReturnStatus.POSTED);

        verify(legacyStockProjectionService).sync(warehouseId, sparePartId);
        verify(auditBuilderService).log(
                eq("repair_material_return"),
                eq(returnId.toString()),
                eq(AuditAction.CREATE),
                eq(AuditModule.REPAIR_MATERIAL_RETURN),
                eq("Возврат материала из ремонта создан"),
                eq(null),
                any(RepairMaterialReturn.class)
        );
    }

    @Test
    void returnRemovedOldPartToDamagedPostsDamagedStatusSoDefaultReservationsCannotUseIt() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId)));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(movementId);
            return movement;
        });
        when(toirStockService.postIncrease(any(StockReceiptCommand.class), eq(StockLedgerMovementType.RETURN)))
                .thenReturn(new WarehouseStockLedger());
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(materialReturnRepository.save(any(RepairMaterialReturn.class))).thenAnswer(invocation -> {
            RepairMaterialReturn materialReturn = invocation.getArgument(0);
            materialReturn.setId(UUID.randomUUID());
            return materialReturn;
        });

        service.returnMaterial(workOrderId, new WorkOrderMaterialReturnRequest(
                null,
                warehouseId,
                sparePartId,
                binId,
                null,
                null,
                null,
                WarehouseStockStatus.DAMAGED,
                BigDecimal.ONE,
                "Removed damaged old part",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RET-DMG",
                List.of(new WmsDocumentGroupRequest("Photo", "PHOTO", "P-1", null, UUID.randomUUID())),
                true
        ));

        ArgumentCaptor<StockReceiptCommand> receiptCaptor = ArgumentCaptor.forClass(StockReceiptCommand.class);
        verify(toirStockService).postIncrease(receiptCaptor.capture(), eq(StockLedgerMovementType.RETURN));
        assertThat(receiptCaptor.getValue().stockStatus()).isEqualTo(WarehouseStockStatus.DAMAGED);
        assertThat(receiptCaptor.getValue().idempotencyKey()).isEqualTo("repair-material-return:" + movementId);
    }

    @Test
    void quarantineReturnRequiresReasonAndPhotoDocumentInStrictMode() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        List<WmsDocumentGroupRequest> documents = List.of(new WmsDocumentGroupRequest(
                "Act",
                "MATERIAL_RETURN_ACT",
                "RET-Q",
                null,
                UUID.randomUUID()
        ));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId)));
        doThrow(RestException.badRequest("Damaged or quarantine return requires PHOTO document"))
                .when(documentPolicyService)
                .validateReturnDocuments(" ", WarehouseStockStatus.QUARANTINE, documents, true);

        assertThatThrownBy(() -> service.returnMaterial(workOrderId, new WorkOrderMaterialReturnRequest(
                null,
                warehouseId,
                sparePartId,
                binId,
                null,
                null,
                null,
                WarehouseStockStatus.QUARANTINE,
                BigDecimal.ONE,
                " ",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RET-Q",
                documents,
                true
        ))).isInstanceOf(RuntimeException.class);

        verify(documentPolicyService).validateReturnDocuments(
                " ",
                WarehouseStockStatus.QUARANTINE,
                documents,
                true
        );
        verify(stockMovementRepository, never()).save(any());
        verify(toirStockService, never()).postIncrease(any(), any());
    }

    @Test
    void returnLinkedToUsageCannotExceedIssuedQuantityMinusPreviousReturns() {
        UUID workOrderId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        RepairMaterialUsage usage = usage(usageId, workOrderId, warehouseId, sparePartId, 5);
        RepairMaterialReturn previous = new RepairMaterialReturn();
        previous.setMaterialUsageId(usageId);
        previous.setQuantity(new BigDecimal("3.0000"));
        previous.setStatus(RepairMaterialReturnStatus.POSTED);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder(workOrderId)));
        when(materialUsageRepository.findByIdAndIsDeletedFalse(usageId)).thenReturn(Optional.of(usage));
        when(materialReturnRepository.findAllByMaterialUsageIdAndStatusAndIsDeletedFalse(usageId, RepairMaterialReturnStatus.POSTED))
                .thenReturn(List.of(previous));

        assertThatThrownBy(() -> service.returnMaterial(workOrderId, new WorkOrderMaterialReturnRequest(
                usageId,
                warehouseId,
                sparePartId,
                usage.getBinId(),
                usage.getLotNumber(),
                usage.getSerialNumber(),
                usage.getExpiryDate(),
                WarehouseStockStatus.AVAILABLE,
                new BigDecimal("2.5000"),
                "Unused after repair",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RET-OVER",
                List.of(),
                false
        ))).isInstanceOf(RuntimeException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(materialReturnRepository, never()).save(any());
    }

    private WorkOrder workOrder(UUID id) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setNumber("WO-1");
        workOrder.setTitle("Repair");
        workOrder.setStatus(WorkOrderStatus.APPROVED);
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setEquipmentId(UUID.randomUUID());
        return workOrder;
    }

    private WorkOrderSparePartRequirement requirement(UUID id, UUID workOrderId, UUID sparePartId, double requiredQty) {
        WorkOrderSparePartRequirement requirement = new WorkOrderSparePartRequirement();
        requirement.setId(id);
        requirement.setWorkOrderId(workOrderId);
        requirement.setSparePartId(sparePartId);
        requirement.setRequiredQty(requiredQty);
        requirement.setUnit("pcs");
        requirement.setStatus(WorkOrderSparePartRequirementStatus.PLANNED);
        return requirement;
    }

    private Reservation reservation(UUID id,
                                    UUID requirementId,
                                    UUID workOrderId,
                                    UUID warehouseId,
                                    UUID sparePartId,
                                    UUID binId,
                                    double quantity) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setRequirementId(requirementId);
        reservation.setWorkOrderId(workOrderId);
        reservation.setWarehouseId(warehouseId);
        reservation.setSparePartId(sparePartId);
        reservation.setBinId(binId);
        reservation.setLotNumber("LOT-7");
        reservation.setSerialNumber("SN-8");
        reservation.setExpiryDate(LocalDate.of(2027, 3, 15));
        reservation.setStockStatus(WarehouseStockStatus.AVAILABLE);
        reservation.setQuantity(quantity);
        reservation.setStatus(ReservationStatus.ACTIVE);
        return reservation;
    }

    private WarehouseTaskDto taskDto(UUID taskId, UUID warehouseId, UUID sparePartId, UUID binId) {
        return new WarehouseTaskDto(
                taskId,
                "WT-2026-00007",
                WarehouseTaskType.PICK,
                WarehouseTaskStatus.OPEN,
                WarehouseTaskPriority.NORMAL,
                warehouseId,
                WarehouseTaskSourceType.WORK_ORDER,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new WarehouseTaskLineDto(
                        UUID.randomUUID(),
                        sparePartId,
                        null,
                        binId,
                        null,
                        "LOT-7",
                        "SN-8",
                        LocalDate.of(2027, 3, 15),
                        WarehouseStockStatus.AVAILABLE,
                        new BigDecimal("4.0000"),
                        BigDecimal.ZERO,
                        "pcs",
                        WarehouseTaskLineStatus.OPEN,
                        false,
                        null
                )),
                Instant.now(),
                Instant.now()
        );
    }

    private WarehouseTask pickTask(UUID id, UUID lineId, UUID workOrderId, UUID warehouseId, UUID sparePartId, UUID binId) {
        WarehouseTask task = new WarehouseTask();
        task.setId(id);
        task.setTaskNumber("WT-2026-00007");
        task.setTaskType(WarehouseTaskType.PICK);
        task.setStatus(WarehouseTaskStatus.IN_PROGRESS);
        task.setWarehouseId(warehouseId);
        task.setSourceType(WarehouseTaskSourceType.WORK_ORDER);
        task.setSourceId(workOrderId);
        WarehouseTaskLine line = new WarehouseTaskLine();
        line.setId(lineId);
        line.setTask(task);
        line.setSparePartId(sparePartId);
        line.setFromBinId(binId);
        line.setLotNumber("LOT-7");
        line.setSerialNumber("SN-8");
        line.setExpiryDate(LocalDate.of(2027, 3, 15));
        line.setStockStatus(WarehouseStockStatus.AVAILABLE);
        line.setPlannedQty(new BigDecimal("4.0000"));
        line.setActualQty(BigDecimal.ZERO);
        line.setStatus(WarehouseTaskLineStatus.IN_PROGRESS);
        task.getLines().add(line);
        return task;
    }

    private RepairMaterialUsage usage(UUID id,
                                      UUID workOrderId,
                                      UUID warehouseId,
                                      UUID sparePartId,
                                      double quantity) {
        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(id);
        usage.setWorkOrderId(workOrderId);
        usage.setWarehouseId(warehouseId);
        usage.setSparePartId(sparePartId);
        usage.setBinId(UUID.randomUUID());
        usage.setLotNumber("LOT-U1");
        usage.setSerialNumber("SN-U1");
        usage.setExpiryDate(LocalDate.of(2028, 5, 20));
        usage.setStockStatus(WarehouseStockStatus.AVAILABLE);
        usage.setQuantity(quantity);
        usage.setUnitCost(15.0);
        return usage;
    }

    private CostCategory costCategory() {
        CostCategory category = new CostCategory();
        category.setId(UUID.randomUUID());
        category.setCode("MATERIALS");
        return category;
    }
}
