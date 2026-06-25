package com.toir.service;

import com.toir.dto.procurement.ProcurementLineRequest;
import com.toir.dto.procurement.ProcurementReceiptLineRequest;
import com.toir.dto.procurement.ProcurementReceiptRequest;
import com.toir.dto.procurement.ProcurementReceiptResponse;
import com.toir.dto.procurement.ProcurementRequestRequest;
import com.toir.entity.PprTask;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.Supplier;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.dto.procurement.EquipmentWarrantyLineRequest;
import com.toir.dto.procurement.ProcurementOrderRequest;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;
import com.toir.enums.StockMovementType;
import com.toir.enums.SupplierType;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.SupplierRepository;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementRequestServiceTest {

    @Mock
    ProcurementRequestRepository repository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    EquipmentTypeRepository equipmentTypeRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    PprTaskRepository pprTaskRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    StockMovementRepository stockMovementRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    SupplierRepository supplierRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    LowStockRecommendationService lowStockRecommendationService;

    @Mock
    ActualCostRepository actualCostRepository;

    @Mock
    CostCategoryRepository costCategoryRepository;

    @Mock
    ToirStockService toirStockService;
    @Mock
    LegacyStockProjectionService legacyStockProjectionService;

    ProcurementRequestService service;

    @BeforeEach
    void setUp() {
        service = new ProcurementRequestService(
                repository,
                sparePartRepository,
                equipmentTypeRepository,
                equipmentRepository,
                warehouseEquipmentItemRepository,
                defectRepository,
                pprTaskRepository,
                departmentRepository,
                stockRepository,
                stockMovementRepository,
                auditBuilderService,
                warehouseRepository,
                scopeAccessService,
                lowStockRecommendationService,
                actualCostRepository,
                costCategoryRepository,
                supplierRepository,
                toirStockService,
                legacyStockProjectionService
        );
    }

    @Test
    void creatingEquipmentRequestDenormalizesSourceAndEquipmentType() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID pprTaskId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        Defect defect = defect(defectId, "Pump bearing destroyed");
        PprTask pprTask = pprTask(pprTaskId, "Quarterly pump overhaul");
        EquipmentType equipmentType = equipmentType(equipmentTypeId, "CNS centrifugal pump");
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(pprTaskId)).thenReturn(Optional.of(pprTask));
        when(equipmentTypeRepository.findByIdAndIsDeletedFalse(equipmentTypeId)).thenReturn(Optional.of(equipmentType));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new ProcurementRequestRequest(
                "Equipment procurement",
                "Procure replacement pump",
                departmentId,
                warehouseId,
                LocalDate.of(2026, 7, 15),
                List.of(new ProcurementLineRequest(null, 2, "PCS", 5_000_000.0, "CNS pump", equipmentTypeId)),
                ProcurementRequestType.EQUIPMENT,
                defectId,
                pprTaskId
        ));

        assertThat(result.type()).isEqualTo(ProcurementRequestType.EQUIPMENT);
        assertThat(result.sourceDefectId()).isEqualTo(defectId);
        assertThat(result.sourceDefectTitle()).isEqualTo("Pump bearing destroyed");
        assertThat(result.sourcePprTaskId()).isEqualTo(pprTaskId);
        assertThat(result.sourcePprTaskTitle()).isEqualTo("Quarterly pump overhaul");
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().equipmentTypeId()).isEqualTo(equipmentTypeId);
        assertThat(result.lines().getFirst().equipmentTypeName()).isEqualTo("CNS centrifugal pump");
        assertThat(result.lines().getFirst().sparePartId()).isNull();
        assertThat(result.totalEstimatedCost()).isEqualTo(10_000_000.0);
    }

    @Test
    void creatingRequestPreservesFrontendResponsibleIdInResponse() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new ProcurementRequestRequest(
                "Spare procurement",
                "Procure bolts",
                departmentId,
                warehouseId,
                LocalDate.of(2026, 7, 15),
                List.of(new ProcurementLineRequest(sparePartId, 4, "PCS", 10.0, "M8 bolts")),
                ProcurementRequestType.SPARE_PART,
                null,
                null,
                responsibleId
        ));

        assertThat(result.responsibleId()).isEqualTo(responsibleId);
        ArgumentCaptor<ProcurementRequest> saved = ArgumentCaptor.forClass(ProcurementRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getResponsibleId()).isEqualTo(responsibleId);
    }

    @Test
    void creatingRequestDefaultsPriorityToMedium() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new ProcurementRequestRequest(
                "Default priority procurement",
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 15),
                List.of(new ProcurementLineRequest(sparePartId, 2, "PCS", 10.0, null))
        ));

        assertThat(result.priority()).isEqualTo(PriorityLevel.MEDIUM);
        ArgumentCaptor<ProcurementRequest> saved = ArgumentCaptor.forClass(ProcurementRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPriority()).isEqualTo(PriorityLevel.MEDIUM);
    }

    @Test
    void creatingRequestPreservesPriorityInResponse() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new ProcurementRequestRequest(
                "Critical procurement",
                "Urgent replacement",
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 15),
                List.of(new ProcurementLineRequest(sparePartId, 1, "PCS", 50.0, "critical")),
                ProcurementRequestType.SPARE_PART,
                null,
                null,
                null,
                PriorityLevel.CRITICAL
        ));

        assertThat(result.priority()).isEqualTo(PriorityLevel.CRITICAL);
        ArgumentCaptor<ProcurementRequest> saved = ArgumentCaptor.forClass(ProcurementRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPriority()).isEqualTo(PriorityLevel.CRITICAL);
    }

    @Test
    void creatingDraftRequestCanStoreScopedSupplier() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID sparePartId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId);
        Supplier supplier = supplier(supplierId, SupplierType.SPARE_PART);
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(supplierRepository.findByIdAndIsDeletedFalse(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(supplier));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new ProcurementRequestRequest(
                "Supplier procurement",
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 15),
                List.of(new ProcurementLineRequest(sparePartId, 2, "PCS", 10.0, null)),
                ProcurementRequestType.SPARE_PART,
                null,
                null,
                null,
                PriorityLevel.MEDIUM,
                supplierId
        ));

        assertThat(result.supplierId()).isEqualTo(supplierId);
        assertThat(result.supplierName()).isEqualTo("Supplier");
        ArgumentCaptor<ProcurementRequest> saved = ArgumentCaptor.forClass(ProcurementRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSupplierId()).isEqualTo(supplierId);
    }

    @Test
    void orderingRequiresSupplierWhenRequestDoesNotHaveOne() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.APPROVED,
                List.of(line(UUID.randomUUID(), 1, null)));
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markOrdered(requestId, new ProcurementOrderRequest(null, null, null, List.of())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Supplier is required");

        verify(repository, never()).save(any());
    }

    @Test
    void orderingValidatesSupplierScopeForProcurementType() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.APPROVED,
                List.of(equipmentLine(UUID.randomUUID(), "CNS pump", 1, null)));
        request.setType(ProcurementRequestType.EQUIPMENT);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(supplierRepository.findByIdAndIsDeletedFalse(supplierId))
                .thenReturn(Optional.of(supplier(supplierId, SupplierType.SPARE_PART)));

        assertThatThrownBy(() -> service.markOrdered(requestId, new ProcurementOrderRequest(supplierId, null, null, List.of())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("EQUIPMENT");

        verify(repository, never()).save(any());
    }

    @Test
    void orderingAppliesEquipmentLineWarrantyDefaults() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        ProcurementRequestLine line = equipmentLine(equipmentTypeId, "CNS pump", 1, null);
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.APPROVED, List.of(line));
        request.setType(ProcurementRequestType.EQUIPMENT);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));
        when(supplierRepository.findByIdAndIsDeletedFalse(supplierId))
                .thenReturn(Optional.of(supplier(supplierId, SupplierType.EQUIPMENT)));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.markOrdered(requestId, new ProcurementOrderRequest(
                supplierId,
                null,
                null,
                List.of(new EquipmentWarrantyLineRequest(
                        line.getId(),
                        true,
                        LocalDate.of(2026, 6, 25),
                        null,
                        12,
                        null
                ))
        ));

        assertThat(result.status()).isEqualTo(ProcurementRequestStatus.ORDERED);
        assertThat(result.supplierId()).isEqualTo(supplierId);
        assertThat(result.lines().getFirst().hasWarranty()).isTrue();
        assertThat(result.lines().getFirst().warrantySupplierId()).isEqualTo(supplierId);
        assertThat(result.lines().getFirst().warrantyStartDate()).isEqualTo(LocalDate.of(2026, 6, 25));
        assertThat(result.lines().getFirst().warrantyEndDate()).isEqualTo(LocalDate.of(2027, 6, 25));
    }

    @Test
    void creatingEquipmentRequestRejectsMixedLineItems() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID equipmentTypeId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(new ProcurementRequestRequest(
                "Invalid mixed procurement",
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 15),
                List.of(new ProcurementLineRequest(sparePartId, 1, "PCS", null, null, equipmentTypeId)),
                ProcurementRequestType.EQUIPMENT,
                null,
                null
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("equipmentTypeId");

        verify(repository, never()).save(any());
    }

    @Test
    void creatingEquipmentRequestRejectsFractionalQuantity() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID equipmentTypeId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(new ProcurementRequestRequest(
                "Invalid quantity",
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 15),
                List.of(new ProcurementLineRequest(null, 1.5, "PCS", null, null, equipmentTypeId)),
                ProcurementRequestType.EQUIPMENT,
                null,
                null
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("whole number");

        verify(repository, never()).save(any());
    }

    @Test
    void receivingOrderedRequestUpdatesExistingStockAndCreatesReceiptMovement() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED,
                List.of(line(sparePartId, 4, 12.5)));
        WarehouseStock stock = stock(warehouseId, sparePartId, 6);
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setQuantity(10);
            return stock;
        });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.markReceived(requestId);

        assertThat(result.status()).isEqualTo(ProcurementRequestStatus.RECEIVED);
        assertThat(result.receivedAt()).isNotNull();
        assertThat(stock.getQuantity()).isEqualTo(10);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.RECEIPT);
        assertThat(movement.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(movement.getSparePartId()).isEqualTo(sparePartId);
        assertThat(movement.getQuantity()).isEqualTo(4);
        assertThat(movement.getUnitCost()).isEqualTo(12.5);
        assertThat(movement.getDocumentNumber()).isEqualTo("PR-2026-0001");
        assertThat(movement.getNotes()).contains(requestId.toString());
        verify(lowStockRecommendationService).evaluateStockSafely(stock);
    }

    @Test
    void receivingCreatesStockRowWhenMissing() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED,
                List.of(line(sparePartId, 3, null)));
        WarehouseStock projectedStock = stock(warehouseId, sparePartId, 3);
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenReturn(projectedStock);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markReceived(requestId);

        verify(legacyStockProjectionService).sync(warehouseId, sparePartId);
        verify(lowStockRecommendationService).evaluateStockSafely(projectedStock);
        verify(stockRepository, never()).save(any());
    }

    @Test
    void receivingCreatesReceiptMovementPerLine() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID firstSparePartId = UUID.randomUUID();
        UUID secondSparePartId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED,
                List.of(line(firstSparePartId, 2, 5.0), line(secondSparePartId, 7, 9.0)));
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));
        when(legacyStockProjectionService.sync(warehouseId, firstSparePartId))
                .thenReturn(stock(warehouseId, firstSparePartId, 3));
        when(legacyStockProjectionService.sync(warehouseId, secondSparePartId))
                .thenReturn(stock(warehouseId, secondSparePartId, 11));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markReceived(requestId);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, org.mockito.Mockito.times(2)).save(movementCaptor.capture());
        assertThat(movementCaptor.getAllValues())
                .extracting(StockMovement::getSparePartId)
                .containsExactly(firstSparePartId, secondSparePartId);
    }

    @Test
    void partialReceiptUpdatesLineProgressAndKeepsRequestPartiallyReceived() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequestLine line = line(sparePartId, 10, 12.5);
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED, List.of(line));
        WarehouseStock stock = stock(warehouseId, sparePartId, 6);
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId)).thenAnswer(invocation -> {
            stock.setQuantity(10);
            return stock;
        });
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcurementReceiptResponse result = service.receiveStock(requestId, new ProcurementReceiptRequest(
                List.of(new ProcurementReceiptLineRequest(line.getId(), 4)),
                LocalDate.of(2026, 6, 18),
                "ACT-1",
                null,
                "partial receipt"
        ));

        assertThat(result.procurementRequest().status()).isEqualTo(ProcurementRequestStatus.PARTIALLY_RECEIVED);
        assertThat(line.getReceivedQuantity()).isEqualTo(4);
        assertThat(line.getRemainingQuantity()).isEqualTo(6);
        assertThat(stock.getQuantity()).isEqualTo(10);
    }

    @Test
    void receivingEquipmentCreatesMovementEquipmentRecordsAndWarehouseItems() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID warrantySupplierId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        ProcurementRequestLine line = equipmentLine(equipmentTypeId, "CNS pump", 2, 5_000_000.0);
        line.setHasWarranty(true);
        line.setWarrantyStartDate(LocalDate.of(2026, 6, 18));
        line.setWarrantyEndDate(LocalDate.of(2027, 6, 18));
        line.setWarrantySupplierId(warrantySupplierId);
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED, List.of(line));
        request.setType(ProcurementRequestType.EQUIPMENT);
        request.setSupplierId(supplierId);
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcurementReceiptResponse result = service.receiveStock(requestId, new ProcurementReceiptRequest(
                List.of(new ProcurementReceiptLineRequest(line.getId(), 2)),
                LocalDate.of(2026, 6, 18),
                "ACT-EQ-1",
                null,
                "equipment receipt"
        ));

        assertThat(result.procurementRequest().status()).isEqualTo(ProcurementRequestStatus.RECEIVED);
        assertThat(result.createdStockMovementIds()).hasSize(1);
        assertThat(result.createdEquipmentIds()).hasSize(2);
        assertThat(line.getReceivedQuantity()).isEqualTo(2);
        assertThat(line.getRemainingQuantity()).isZero();

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.EQUIPMENT_IN);
        assertThat(movement.getSparePartId()).isNull();
        assertThat(movement.getEquipmentTypeId()).isEqualTo(equipmentTypeId);
        assertThat(movement.getSourceLineId()).isEqualTo(line.getId());

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository, org.mockito.Mockito.times(2)).save(equipmentCaptor.capture());
        assertThat(equipmentCaptor.getAllValues())
                .allSatisfy(equipment -> {
                    assertThat(equipment.getEquipmentTypeId()).isEqualTo(equipmentTypeId);
                    assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.STANDBY);
                    assertThat(equipment.getCurrentLocationType()).isEqualTo(EquipmentLocationType.WAREHOUSE);
                    assertThat(equipment.getCurrentWarehouseId()).isEqualTo(warehouseId);
                    assertThat(equipment.getProcurementRequestId()).isEqualTo(requestId);
                    assertThat(equipment.getProcurementRequestLineId()).isEqualTo(line.getId());
                    assertThat(equipment.getProcurementStockMovementId()).isEqualTo(movement.getId());
                    assertThat(equipment.getSupplierId()).isEqualTo(supplierId);
                    assertThat(equipment.getHasWarranty()).isTrue();
                    assertThat(equipment.getWarrantySupplierId()).isEqualTo(warrantySupplierId);
                    assertThat(equipment.getWarrantyStartDate()).isEqualTo(LocalDate.of(2026, 6, 18));
                    assertThat(equipment.getWarrantyEndDate()).isEqualTo(LocalDate.of(2027, 6, 18));
                });

        ArgumentCaptor<WarehouseEquipmentItem> itemCaptor = ArgumentCaptor.forClass(WarehouseEquipmentItem.class);
        verify(warehouseEquipmentItemRepository, org.mockito.Mockito.times(2)).save(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .allSatisfy(item -> {
                    assertThat(item.getWarehouseId()).isEqualTo(warehouseId);
                    assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.AVAILABLE);
                    assertThat(item.isActive()).isTrue();
                });
        verify(stockRepository, never()).save(any());
        verify(toirStockService, never()).postReceipt(any());
    }

    @Test
    void fullReceiptMarksReceivedAndCreatesOneMovementPerReceivedLine() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequestLine line = line(sparePartId, 10, 12.5);
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED, List.of(line));
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId))
                .thenReturn(stock(warehouseId, sparePartId, 16));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcurementReceiptResponse result = service.receiveStock(requestId, new ProcurementReceiptRequest(
                List.of(new ProcurementReceiptLineRequest(line.getId(), 10)),
                LocalDate.of(2026, 6, 18),
                "ACT-2",
                null,
                "full receipt"
        ));

        assertThat(result.procurementRequest().status()).isEqualTo(ProcurementRequestStatus.RECEIVED);
        assertThat(result.createdStockMovementIds()).hasSize(1);
        assertThat(line.getReceivedQuantity()).isEqualTo(10);
        assertThat(line.getRemainingQuantity()).isZero();
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void receivingLineWithUnitPriceCreatesSourceLinkedPendingActualCost() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED,
                List.of(line(sparePartId, 2, 11.0)));
        CostCategory category = new CostCategory();
        category.setId(categoryId);
        category.setCode("MATERIALS");
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId))
                .thenReturn(stock(warehouseId, sparePartId, 3));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("MATERIALS")).thenReturn(Optional.of(category));
        when(actualCostRepository.findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        service.markReceived(requestId);

        ArgumentCaptor<ActualCost> costCaptor = ArgumentCaptor.forClass(ActualCost.class);
        verify(actualCostRepository).save(costCaptor.capture());
        ActualCost cost = costCaptor.getValue();
        assertThat(cost.getSourceType()).isEqualTo(ActualCostSourceType.PROCUREMENT_RECEIPT);
        assertThat(cost.getSourceId()).isNotNull();
        assertThat(cost.getCostCategoryId()).isEqualTo(categoryId);
        assertThat(cost.getStatus()).isEqualTo(ActualCostStatus.PENDING);
        assertThat(cost.getAmount()).isEqualTo(22.0);
        assertThat(cost.getNotes()).contains(requestId.toString());
    }

    @Test
    void receivingLineWithUnitPriceUpdatesExistingProcurementReceiptActualCostWithoutDuplicate() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        UUID existingCostId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, warehouseId, ProcurementRequestStatus.ORDERED,
                List.of(line(sparePartId, 4, 12.0)));
        CostCategory category = new CostCategory();
        category.setId(categoryId);
        category.setCode("MATERIALS");
        ActualCost existingCost = new ActualCost();
        existingCost.setId(existingCostId);
        existingCost.setSourceType(ActualCostSourceType.PROCUREMENT_RECEIPT);
        existingCost.setSourceId(movementId);
        existingCost.setAmount(10.0);
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));
        when(legacyStockProjectionService.sync(warehouseId, sparePartId))
                .thenReturn(stock(warehouseId, sparePartId, 5));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(movementId);
            return movement;
        });
        when(repository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("MATERIALS")).thenReturn(Optional.of(category));
        when(actualCostRepository.findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                ActualCostSourceType.PROCUREMENT_RECEIPT, movementId)).thenReturn(Optional.of(existingCost));

        service.markReceived(requestId);

        ArgumentCaptor<ActualCost> costCaptor = ArgumentCaptor.forClass(ActualCost.class);
        verify(actualCostRepository).save(costCaptor.capture());
        ActualCost cost = costCaptor.getValue();
        assertThat(cost.getId()).isEqualTo(existingCostId);
        assertThat(cost.getSourceType()).isEqualTo(ActualCostSourceType.PROCUREMENT_RECEIPT);
        assertThat(cost.getSourceId()).isEqualTo(movementId);
        assertThat(cost.getCostCategoryId()).isEqualTo(categoryId);
        assertThat(cost.getAmount()).isEqualTo(48.0);
    }

    @Test
    void repeatedReceiveIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.RECEIVED,
                List.of(line(UUID.randomUUID(), 1, null)));
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("already RECEIVED");

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void receivingWithoutWarehouseIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, null, ProcurementRequestStatus.ORDERED,
                List.of(line(UUID.randomUUID(), 1, null)));
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseId is required");

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void receivingWithoutLinesIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.ORDERED, List.of());
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("at least one line");

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void receivingInvalidStatusesIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        for (ProcurementRequestStatus status : List.of(
                ProcurementRequestStatus.DRAFT,
                ProcurementRequestStatus.SUBMITTED,
                ProcurementRequestStatus.APPROVED,
                ProcurementRequestStatus.CANCELLED,
                ProcurementRequestStatus.REJECTED)) {
            UUID requestId = UUID.randomUUID();
            ProcurementRequest request = request(requestId, UUID.randomUUID(), status,
                    List.of(line(UUID.randomUUID(), 1, null)));
            when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.markReceived(requestId))
                    .isInstanceOf(RestException.class)
                    .hasMessageContaining("Only ORDERED or PARTIALLY_RECEIVED can be marked RECEIVED");
        }

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void receivingLineWithoutSparePartIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.ORDERED,
                List.of(line(null, 1, null)));
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("sparePartId is required");

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void receivingLineWithNonPositiveQuantityIsBlocked() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        ProcurementRequest request = request(requestId, UUID.randomUUID(), ProcurementRequestStatus.ORDERED,
                List.of(line(UUID.randomUUID(), 0, null)));
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("quantity must be greater than 0");

        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void missingRequestRemains404OnReceive() {
        UUID requestId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalseForUpdate(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markReceived(requestId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Procurement request not found");

        verify(repository, never()).save(any());
        verify(stockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(sparePartRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    private ProcurementRequest request(UUID id,
                                       UUID warehouseId,
                                       ProcurementRequestStatus status,
                                       List<ProcurementRequestLine> lines) {
        ProcurementRequest request = new ProcurementRequest();
        request.setId(id);
        request.setNumber("PR-2026-0001");
        request.setTitle("Procurement");
        request.setWarehouseId(warehouseId);
        request.setStatus(status);
        for (ProcurementRequestLine line : lines) {
            line.setRequest(request);
            request.getLines().add(line);
        }
        return request;
    }

    private ProcurementRequestLine line(UUID sparePartId, double quantity, Double unitPrice) {
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setId(UUID.randomUUID());
        line.setSparePartId(sparePartId);
        line.setQuantity(quantity);
        line.setReceivedQuantity(0);
        line.setRemainingQuantity(quantity);
        line.setUnit("pcs");
        line.setUnitPrice(unitPrice);
        line.setEstimatedCost(unitPrice == null ? 0 : unitPrice * quantity);
        return line;
    }

    private ProcurementRequestLine equipmentLine(UUID equipmentTypeId,
                                                 String equipmentTypeName,
                                                 double quantity,
                                                 Double unitPrice) {
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setId(UUID.randomUUID());
        line.setEquipmentTypeId(equipmentTypeId);
        line.setEquipmentTypeName(equipmentTypeName);
        line.setQuantity(quantity);
        line.setReceivedQuantity(0);
        line.setRemainingQuantity(quantity);
        line.setUnit("PCS");
        line.setUnitPrice(unitPrice);
        line.setEstimatedCost(unitPrice == null ? 0 : unitPrice * quantity);
        return line;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity) {
        WarehouseStock stock = new WarehouseStock();
        stock.setId(UUID.randomUUID());
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(0);
        stock.setMinQty(0);
        return stock;
    }

    private SparePart sparePart(UUID sparePartId) {
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setCode("SP-1");
        sparePart.setName("Bearing");
        sparePart.setUnit("pcs");
        return sparePart;
    }

    private Supplier supplier(UUID supplierId, SupplierType supplierType) {
        Supplier supplier = new Supplier();
        supplier.setId(supplierId);
        supplier.setCode("SUP-1");
        supplier.setName("Supplier");
        supplier.setActive(true);
        supplier.setSupplierType(supplierType);
        return supplier;
    }

    private EquipmentType equipmentType(UUID id, String name) {
        EquipmentType equipmentType = new EquipmentType();
        equipmentType.setId(id);
        equipmentType.setCode("ET-1");
        equipmentType.setName(name);
        equipmentType.setCategory("PRODUCTION_EQUIPMENT");
        return equipmentType;
    }

    private Defect defect(UUID id, String title) {
        Defect defect = new Defect();
        defect.setId(id);
        defect.setCode("DF-1");
        defect.setTitle(title);
        defect.setDescription(title);
        defect.setEquipmentId(UUID.randomUUID());
        return defect;
    }

    private PprTask pprTask(UUID id, String title) {
        PprTask task = new PprTask();
        task.setId(id);
        task.setCode("PPR-TASK-1");
        task.setTitle(title);
        return task;
    }
}
