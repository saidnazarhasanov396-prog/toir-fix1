package com.toir.service;

import com.toir.dto.workorder.WorkOrderMaterialReadinessDto;
import com.toir.entity.Reservation;
import com.toir.entity.SparePart;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.RepairMaterialReturn;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.enums.MaterialReadinessStatus;
import com.toir.enums.RepairMaterialReturnStatus;
import com.toir.enums.ReservationStatus;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.RepairMaterialReturnRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.PurchaseOrderRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderMaterialReadinessServiceTest {

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    WorkOrderSparePartRequirementRepository requirementRepository;

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    RepairMaterialUsageRepository materialUsageRepository;

    @Mock
    RepairMaterialReturnRepository materialReturnRepository;

    @Mock
    WarehouseStockBalanceRepository warehouseStockBalanceRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    PurchaseOrderRepository purchaseOrderRepository;

    WorkOrderMaterialReadinessService service;

    UUID workOrderId;
    UUID equipmentId;
    WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        service = new WorkOrderMaterialReadinessService(
                workOrderRepository,
                requirementRepository,
                reservationRepository,
                materialUsageRepository,
                materialReturnRepository,
                warehouseStockBalanceRepository,
                warehouseRepository,
                purchaseOrderRepository
        );
        workOrderId = UUID.randomUUID();
        equipmentId = UUID.randomUUID();
        workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setEquipmentId(equipmentId);
    }

    @Test
    void noRequirementsAreNotRequiredAndNonBlocking() {
        givenMaterialState(List.of(), List.of(), List.of(), List.of());

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(result.equipmentId()).isEqualTo(equipmentId);
        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.NOT_REQUIRED);
        assertThat(result.blocking()).isFalse();
        assertThat(result.checkedAt()).isNotNull();
        assertThat(result.rows()).isEmpty();
    }

    @Test
    void fullyReservedRequirementIsReserved() {
        WorkOrderSparePartRequirement requirement = requirement(10, "pcs", "Bearing", "Needs kit");
        Reservation reservation = reservation(requirement.getId(), requirement.getSparePartId(), 10);
        givenMaterialState(List.of(requirement), List.of(reservation), List.of(), List.of());

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.RESERVED);
        assertThat(result.blocking()).isFalse();
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.RESERVED);
            assertThat(row.requiredQty()).isEqualByComparingTo("10");
            assertThat(row.reservedQty()).isEqualByComparingTo("10");
            assertThat(row.shortageQty()).isZero();
            assertThat(row.blocking()).isFalse();
            assertThat(row.sparePartName()).isEqualTo("Bearing");
            assertThat(row.unit()).isEqualTo("pcs");
            assertThat(row.notes()).isEqualTo("Needs kit");
            assertThat(row.expectedDate()).isNull();
            assertThat(row.sourceSystem()).isEqualTo("TOIR_WMS");
            assertThat(row.lastSyncedAt()).isNotNull();
            assertThat(row.nextAction()).isEqualTo("NONE");
        });
    }

    @Test
    void availableStockAtWorkOrderWarehouseOffersExactReservation() {
        UUID warehouseId = UUID.randomUUID();
        workOrder.setWarehouseId(warehouseId);
        WorkOrderSparePartRequirement requirement = requirement(10, "pcs", "Bearing", null);
        Warehouse warehouse = warehouse(warehouseId, "WH-01", "Main warehouse");
        WarehouseStockBalance balance = stock(warehouseId, requirement.getSparePartId(), 12, 2);
        givenMaterialState(List.of(requirement), List.of(), List.of(), List.of());
        when(warehouseStockBalanceRepository.findAllBySparePartIdAndIsDeletedFalse(requirement.getSparePartId()))
                .thenReturn(List.of(balance));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId))).thenReturn(List.of(warehouse));

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.AVAILABLE);
        assertThat(result.blocking()).isTrue();
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.AVAILABLE);
            assertThat(row.sourceWarehouseId()).isEqualTo(warehouseId);
            assertThat(row.sourceWarehouseCode()).isEqualTo("WH-01");
            assertThat(row.sourceWarehouseName()).isEqualTo("Main warehouse");
            assertThat(row.onHandQty()).isEqualByComparingTo("12");
            assertThat(row.wmsReservedQty()).isEqualByComparingTo("2");
            assertThat(row.availableQty()).isEqualByComparingTo("10");
            assertThat(row.shortageQty()).isZero();
            assertThat(row.nextAction()).isEqualTo("RESERVE");
            assertThat(row.nextActionQty()).isEqualByComparingTo("10");
            assertThat(row.sourceSystem()).isEqualTo("TOIR_WMS");
            assertThat(row.lastSyncedAt()).isNotNull();
        });
    }

    @Test
    void stockAtAnotherWarehouseOffersTransfer() {
        UUID targetWarehouseId = UUID.randomUUID();
        UUID sourceWarehouseId = UUID.randomUUID();
        workOrder.setWarehouseId(targetWarehouseId);
        WorkOrderSparePartRequirement requirement = requirement(6, "pcs", "Seal", null);
        givenMaterialState(List.of(requirement), List.of(), List.of(), List.of());
        when(warehouseStockBalanceRepository.findAllBySparePartIdAndIsDeletedFalse(requirement.getSparePartId()))
                .thenReturn(List.of(stock(sourceWarehouseId, requirement.getSparePartId(), 8, 1)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(sourceWarehouseId)))
                .thenReturn(List.of(warehouse(sourceWarehouseId, "WH-02", "Remote warehouse")));

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.TRANSFER_REQUIRED);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.sourceWarehouseId()).isEqualTo(sourceWarehouseId);
            assertThat(row.availableQty()).isEqualByComparingTo("7");
            assertThat(row.shortageQty()).isZero();
            assertThat(row.nextAction()).isEqualTo("TRANSFER");
            assertThat(row.nextActionQty()).isEqualByComparingTo("6");
        });
    }

    @Test
    void partialGlobalStockOffersProcurementOnlyForActualShortage() {
        UUID warehouseId = UUID.randomUUID();
        workOrder.setWarehouseId(warehouseId);
        WorkOrderSparePartRequirement requirement = requirement(10, "pcs", "Filter", null);
        givenMaterialState(List.of(requirement), List.of(), List.of(), List.of());
        when(warehouseStockBalanceRepository.findAllBySparePartIdAndIsDeletedFalse(requirement.getSparePartId()))
                .thenReturn(List.of(stock(warehouseId, requirement.getSparePartId(), 5, 1)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId, "WH-01", "Main warehouse")));

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.PARTIALLY_AVAILABLE);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.availableQty()).isEqualByComparingTo("4");
            assertThat(row.shortageQty()).isEqualByComparingTo("6");
            assertThat(row.nextAction()).isEqualTo("CREATE_PROCUREMENT");
            assertThat(row.nextActionQty()).isEqualByComparingTo("6");
        });
    }

    @Test
    void unavailableWmsIsExplicitAndBlocking() {
        WorkOrderSparePartRequirement requirement = requirement(2, "pcs", "Belt", null);
        givenMaterialState(List.of(requirement), List.of(), List.of(), List.of());
        when(warehouseStockBalanceRepository.findAllBySparePartIdAndIsDeletedFalse(requirement.getSparePartId()))
                .thenThrow(new IllegalStateException("WMS database is unavailable"));

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.WMS_UNAVAILABLE);
        assertThat(result.blocking()).isTrue();
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.WMS_UNAVAILABLE);
            assertThat(row.nextAction()).isEqualTo("NONE");
        });
    }

    @Test
    void openSupplierOrderProvidesExpectedDeliveryDate() {
        UUID warehouseId = UUID.randomUUID();
        workOrder.setWarehouseId(warehouseId);
        WorkOrderSparePartRequirement requirement = requirement(3, "pcs", "Bearing", null);
        givenMaterialState(List.of(requirement), List.of(), List.of(), List.of());
        java.time.LocalDate expectedDelivery = java.time.LocalDate.of(2026, 8, 12);
        when(purchaseOrderRepository.findEarliestExpectedDeliveryDate(
                warehouseId, requirement.getSparePartId())).thenReturn(expectedDelivery);

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.rows()).singleElement().satisfies(row ->
                assertThat(row.expectedDate()).isEqualTo(expectedDelivery));
    }

    @Test
    void fullyIssuedRequirementIsIssued() {
        WorkOrderSparePartRequirement requirement = requirement(5, "l", "Oil", null);
        RepairMaterialUsage usage = usage(requirement.getId(), requirement.getSparePartId(), 5);
        givenMaterialState(List.of(requirement), List.of(), List.of(usage), List.of());

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.ISSUED);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.ISSUED);
            assertThat(row.issuedQty()).isEqualByComparingTo("5");
            assertThat(row.shortageQty()).isZero();
        });
    }

    @Test
    void partialCoverageWithoutStockRequiresProcurement() {
        WorkOrderSparePartRequirement requirement = requirement(8, "pcs", "Seal", null);
        givenMaterialState(
                List.of(requirement),
                List.of(reservation(requirement.getId(), requirement.getSparePartId(), 2)),
                List.of(usage(requirement.getId(), requirement.getSparePartId(), 3)),
                List.of()
        );

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.PROCUREMENT_REQUIRED);
        assertThat(result.blocking()).isTrue();
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.PROCUREMENT_REQUIRED);
            assertThat(row.shortageQty()).isEqualByComparingTo("3");
            assertThat(row.blocking()).isTrue();
            assertThat(row.nextAction()).isEqualTo("CREATE_PROCUREMENT");
            assertThat(row.nextActionQty()).isEqualByComparingTo("3");
        });
    }

    @Test
    void noCoverageRequiresProcurement() {
        WorkOrderSparePartRequirement requirement = requirement(4, "pcs", "Filter", null);
        givenMaterialState(List.of(requirement), List.of(), List.of(), List.of());

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.PROCUREMENT_REQUIRED);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.PROCUREMENT_REQUIRED);
            assertThat(row.shortageQty()).isEqualByComparingTo("4");
            assertThat(row.blocking()).isTrue();
        });
    }

    @Test
    void returnedMaterialReducesNetIssuedAndDoesNotHideShortage() {
        WorkOrderSparePartRequirement requirement = requirement(5, "pcs", "Belt", null);
        RepairMaterialUsage usage = usage(requirement.getId(), requirement.getSparePartId(), 5);
        RepairMaterialReturn returned = returned(usage.getId(), requirement.getSparePartId(), 2);
        givenMaterialState(List.of(requirement), List.of(), List.of(usage), List.of(returned));

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.PROCUREMENT_REQUIRED);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.issuedQty()).isEqualByComparingTo("5");
            assertThat(row.returnedQty()).isEqualByComparingTo("2");
            assertThat(row.shortageQty()).isEqualByComparingTo("2");
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.PROCUREMENT_REQUIRED);
        });
    }

    @Test
    void multipleRequirementsPreferProcurementRequirementForOverall() {
        WorkOrderSparePartRequirement ready = requirement(3, "pcs", "Ready part", null);
        WorkOrderSparePartRequirement partial = requirement(4, "pcs", "Partial part", null);
        WorkOrderSparePartRequirement shortage = requirement(2, "pcs", "Missing part", null);
        givenMaterialState(
                List.of(ready, partial, shortage),
                List.of(reservation(ready.getId(), ready.getSparePartId(), 3)),
                List.of(usage(partial.getId(), partial.getSparePartId(), 1)),
                List.of()
        );

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.PROCUREMENT_REQUIRED);
        assertThat(result.rows()).extracting("readinessStatus")
                .containsExactly(
                        MaterialReadinessStatus.RESERVED,
                        MaterialReadinessStatus.PROCUREMENT_REQUIRED,
                        MaterialReadinessStatus.PROCUREMENT_REQUIRED
                );
    }

    @Test
    void zeroRequiredQuantityIsNotRequiredAndNullSafe() {
        WorkOrderSparePartRequirement requirement = requirement(0, null, null, null);
        requirement.setSparePart(null);
        givenMaterialState(List.of(requirement), List.of(), List.of(), List.of());

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.NOT_REQUIRED);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.NOT_REQUIRED);
            assertThat(row.blocking()).isFalse();
            assertThat(row.sparePartName()).isNull();
            assertThat(row.unit()).isNull();
        });
    }

    @Test
    void fallsBackToWorkOrderAndSparePartWhenRequirementLinkIsMissing() {
        WorkOrderSparePartRequirement requirement = requirement(6, "pcs", "Fallback part", null);
        givenMaterialState(
                List.of(requirement),
                List.of(reservation(null, requirement.getSparePartId(), 2)),
                List.of(usage(null, requirement.getSparePartId(), 4)),
                List.of()
        );

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.RESERVED);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.reservedQty()).isEqualByComparingTo("2");
            assertThat(row.issuedQty()).isEqualByComparingTo("4");
            assertThat(row.shortageQty()).isZero();
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.RESERVED);
        });
    }

    @Test
    void missingWorkOrderThrowsNotFound() {
        UUID missingId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReadiness(missingId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Work order not found");
    }

    private void givenMaterialState(
            List<WorkOrderSparePartRequirement> requirements,
            List<Reservation> reservations,
            List<RepairMaterialUsage> usages,
            List<RepairMaterialReturn> returns
    ) {
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(requirementRepository.findActiveByWorkOrderId(workOrderId)).thenReturn(requirements);
        when(reservationRepository.findAllByWorkOrderIdAndStatusAndIsDeletedFalseOrderByUpdatedAtDesc(
                workOrderId,
                ReservationStatus.ACTIVE
        )).thenReturn(reservations);
        when(materialUsageRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(usages);
        when(materialReturnRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(returns);
        requirements.stream()
                .map(WorkOrderSparePartRequirement::getSparePartId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(sparePartId -> when(warehouseStockBalanceRepository
                        .findAllBySparePartIdAndIsDeletedFalse(sparePartId)).thenReturn(List.of()));
    }

    private WorkOrderSparePartRequirement requirement(
            double requiredQty,
            String unit,
            String sparePartName,
            String notes
    ) {
        UUID requirementId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setName(sparePartName);
        WorkOrderSparePartRequirement requirement = new WorkOrderSparePartRequirement();
        requirement.setId(requirementId);
        requirement.setWorkOrder(workOrder);
        requirement.setWorkOrderId(workOrderId);
        requirement.setSparePart(sparePart);
        requirement.setSparePartId(sparePartId);
        requirement.setRequiredQty(java.math.BigDecimal.valueOf(requiredQty));
        requirement.setUnit(unit);
        requirement.setNotes(notes);
        return requirement;
    }

    private Reservation reservation(UUID requirementId, UUID sparePartId, double quantity) {
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setWorkOrderId(workOrderId);
        reservation.setRequirementId(requirementId);
        reservation.setSparePartId(sparePartId);
        reservation.setQuantity(java.math.BigDecimal.valueOf(quantity));
        reservation.setStatus(ReservationStatus.ACTIVE);
        return reservation;
    }

    private RepairMaterialUsage usage(UUID requirementId, UUID sparePartId, double quantity) {
        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(UUID.randomUUID());
        usage.setWorkOrderId(workOrderId);
        usage.setRequirementId(requirementId);
        usage.setSparePartId(sparePartId);
        usage.setQuantity(new java.math.BigDecimal(Double.toString(quantity)));
        return usage;
    }

    private RepairMaterialReturn returned(UUID usageId, UUID sparePartId, double quantity) {
        RepairMaterialReturn returned = new RepairMaterialReturn();
        returned.setId(UUID.randomUUID());
        returned.setWorkOrderId(workOrderId);
        returned.setMaterialUsageId(usageId);
        returned.setSparePartId(sparePartId);
        returned.setQuantity(BigDecimal.valueOf(quantity));
        returned.setStatus(RepairMaterialReturnStatus.POSTED);
        return returned;
    }

    private Warehouse warehouse(UUID id, String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setCode(code);
        warehouse.setName(name);
        return warehouse;
    }

    private WarehouseStockBalance stock(UUID warehouseId, UUID sparePartId, double onHand, double reserved) {
        WarehouseStockBalance balance = new WarehouseStockBalance();
        balance.setId(UUID.randomUUID());
        balance.setWarehouseId(warehouseId);
        balance.setSparePartId(sparePartId);
        balance.setStockStatus(WarehouseStockStatus.AVAILABLE);
        balance.setQtyOnHand(BigDecimal.valueOf(onHand));
        balance.setQtyReserved(BigDecimal.valueOf(reserved));
        balance.setUpdatedAt(java.time.Instant.now().minusSeconds(120));
        return balance;
    }
}
