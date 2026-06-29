package com.toir.wms;

import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.entity.SparePart;
import com.toir.entity.SparePartType;
import com.toir.entity.StockMovement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.warehouse.InventoryCountLine;
import com.toir.entity.warehouse.InventoryCountSession;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseBin;
import com.toir.entity.warehouse.WarehouseReservationLedger;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.entity.warehouse.WarehouseStockLedger;
import com.toir.entity.warehouse.WarehouseTask;
import com.toir.entity.warehouse.WarehouseTaskLine;
import com.toir.enums.InventoryCountLineStatus;
import com.toir.enums.InventoryCountScopeType;
import com.toir.enums.InventoryCountSessionStatus;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.PriorityLevel;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskLineStatus;
import com.toir.enums.WarehouseTaskPriority;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.enums.WorkOrderSparePartRequirementSourceType;
import com.toir.enums.WorkOrderSparePartRequirementStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.repository.InventoryCountLineRepository;
import com.toir.repository.InventoryCountSessionRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.SparePartTypeRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseBinRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseReservationLedgerRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseStockLedgerRepository;
import com.toir.repository.WarehouseTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.service.warehouse.ToirStockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wms_smoke;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ToirStockService.class)
class WmsEndToEndSmokeTest {

    private static final String LOT = "LOT-E2E";
    private static final String SERIAL = "SN-E2E";
    private static final LocalDate EXPIRY = LocalDate.of(2028, 1, 31);

    @Autowired
    ToirStockService stockService;

    @Autowired
    WarehouseRepository warehouseRepository;

    @Autowired
    WarehouseBinRepository binRepository;

    @Autowired
    SparePartTypeRepository sparePartTypeRepository;

    @Autowired
    SparePartRepository sparePartRepository;

    @Autowired
    WorkOrderRepository workOrderRepository;

    @Autowired
    WorkOrderSparePartRequirementRepository requirementRepository;

    @Autowired
    WarehouseTaskRepository taskRepository;

    @Autowired
    InventoryCountSessionRepository countSessionRepository;

    @Autowired
    InventoryCountLineRepository countLineRepository;

    @Autowired
    StockMovementRepository stockMovementRepository;

    @Autowired
    WarehouseStockBalanceRepository balanceRepository;

    @Autowired
    WarehouseStockLedgerRepository stockLedgerRepository;

    @Autowired
    WarehouseReservationLedgerRepository reservationLedgerRepository;

    @Test
    void completeWmsOperationalPathMaintainsLedgerAndBalanceIntegrity() {
        Warehouse warehouse = warehouseRepository.saveAndFlush(warehouse());
        WarehouseBin bin = binRepository.saveAndFlush(bin(warehouse.getId()));
        SparePart sparePart = sparePartRepository.saveAndFlush(sparePart());
        WorkOrder workOrder = workOrderRepository.saveAndFlush(workOrder(warehouse.getId()));
        WorkOrderSparePartRequirement requirement = requirementRepository.saveAndFlush(
                requirement(workOrder, sparePart)
        );

        StockMovement receiptMovement = stockMovementRepository.saveAndFlush(stockMovement(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                StockMovementType.RECEIPT,
                StockMovementSourceType.PURCHASE_ORDER,
                UUID.randomUUID(),
                new BigDecimal("10.0000"),
                WarehouseStockStatus.AVAILABLE,
                "PO-RCV-1"
        ));
        stockService.postReceipt(new StockReceiptCommand(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                new BigDecimal("10.0000"),
                new BigDecimal("5.0000"),
                LOT,
                SERIAL,
                EXPIRY,
                WarehouseStockStatus.AVAILABLE,
                "STOCK_MOVEMENT",
                receiptMovement.getId(),
                receiptMovement.getDocumentNumber(),
                "purchase order receipt",
                "smoke-receipt"
        ));

        stockService.postReceipt(new StockReceiptCommand(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                new BigDecimal("2.0000"),
                new BigDecimal("5.0000"),
                LOT,
                "SN-QUARANTINE",
                EXPIRY,
                WarehouseStockStatus.QUARANTINE,
                "QUALITY_HOLD",
                UUID.randomUUID(),
                "Q-HOLD-1",
                "quality hold receipt",
                "smoke-quarantine-receipt"
        ));

        WarehouseReservationLedger reserveLedger = stockService.reserve(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                LOT,
                SERIAL,
                EXPIRY,
                WarehouseStockStatus.AVAILABLE,
                new BigDecimal("4.0000"),
                "WORK_ORDER_SPARE_PART_REQUIREMENT",
                requirement.getId(),
                workOrder.getNumber(),
                "smoke-reserve"
        );
        assertThat(reserveLedger.getMovementType()).isEqualTo(StockLedgerMovementType.RESERVE);

        WarehouseTask pickTask = taskRepository.saveAndFlush(pickTask(
                warehouse.getId(),
                workOrder.getId(),
                sparePart.getId(),
                bin.getId()
        ));
        WarehouseTaskLine pickLine = pickTask.getLines().getFirst();
        pickLine.setScanConfirmed(true);
        pickLine.setActualQty(new BigDecimal("3.0000"));
        pickLine.setStatus(WarehouseTaskLineStatus.DONE);
        taskRepository.saveAndFlush(pickTask);

        stockService.releaseReservation(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                LOT,
                SERIAL,
                EXPIRY,
                WarehouseStockStatus.AVAILABLE,
                new BigDecimal("4.0000"),
                "WAREHOUSE_TASK",
                pickTask.getId(),
                pickTask.getTaskNumber(),
                "smoke-release"
        );

        StockMovement issueMovement = stockMovementRepository.saveAndFlush(stockMovement(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                StockMovementType.ISSUE,
                StockMovementSourceType.WORK_ORDER_MATERIAL_USAGE,
                workOrder.getId(),
                new BigDecimal("3.0000"),
                WarehouseStockStatus.AVAILABLE,
                "WO-ISS-1"
        ));
        stockService.postIssue(new StockIssueCommand(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                new BigDecimal("3.0000"),
                LOT,
                SERIAL,
                EXPIRY,
                WarehouseStockStatus.AVAILABLE,
                "STOCK_MOVEMENT",
                issueMovement.getId(),
                issueMovement.getDocumentNumber(),
                "issued from pick task",
                "smoke-issue"
        ));

        StockMovement returnMovement = stockMovementRepository.saveAndFlush(stockMovement(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                StockMovementType.RETURN,
                StockMovementSourceType.REPAIR_MATERIAL_RETURN,
                workOrder.getId(),
                new BigDecimal("1.0000"),
                WarehouseStockStatus.AVAILABLE,
                "WO-RET-1"
        ));
        stockService.postIncrease(new StockReceiptCommand(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                new BigDecimal("1.0000"),
                new BigDecimal("5.0000"),
                LOT,
                SERIAL,
                EXPIRY,
                WarehouseStockStatus.AVAILABLE,
                "STOCK_MOVEMENT",
                returnMovement.getId(),
                returnMovement.getDocumentNumber(),
                "unused material return",
                "smoke-return"
        ), StockLedgerMovementType.RETURN);

        InventoryCountSession countSession = countSessionRepository.saveAndFlush(countSession(
                warehouse.getId(),
                bin.getId()
        ));
        countLineRepository.saveAndFlush(countLine(
                countSession.getId(),
                warehouse.getId(),
                bin.getId(),
                sparePart.getId()
        ));

        StockMovement adjustmentMovement = stockMovementRepository.saveAndFlush(stockMovement(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                StockMovementType.ADJUSTMENT,
                StockMovementSourceType.INVENTORY_COUNT_SESSION,
                countSession.getId(),
                new BigDecimal("2.0000"),
                WarehouseStockStatus.AVAILABLE,
                "IC-POST-1"
        ));
        stockService.postDecrease(new StockIssueCommand(
                warehouse.getId(),
                sparePart.getId(),
                bin.getId(),
                new BigDecimal("2.0000"),
                LOT,
                SERIAL,
                EXPIRY,
                WarehouseStockStatus.AVAILABLE,
                "STOCK_MOVEMENT",
                adjustmentMovement.getId(),
                adjustmentMovement.getDocumentNumber(),
                "physical count adjustment",
                "smoke-adjustment-dec"
        ), StockLedgerMovementType.ADJUSTMENT_DEC);
        countSession.setStatus(InventoryCountSessionStatus.POSTED);
        countSession.setPostedAt(Instant.now());
        countSessionRepository.saveAndFlush(countSession);

        WarehouseStockBalance availableBalance = balanceRepository.findByIdentityKeyAndIsDeletedFalse(
                WarehouseStockBalance.buildIdentityKey(
                        warehouse.getId(),
                        sparePart.getId(),
                        bin.getId(),
                        LOT,
                        SERIAL,
                        EXPIRY,
                        WarehouseStockStatus.AVAILABLE
                )
        ).orElseThrow();
        WarehouseStockBalance quarantineBalance = balanceRepository.findByIdentityKeyAndIsDeletedFalse(
                WarehouseStockBalance.buildIdentityKey(
                        warehouse.getId(),
                        sparePart.getId(),
                        bin.getId(),
                        LOT,
                        "SN-QUARANTINE",
                        EXPIRY,
                        WarehouseStockStatus.QUARANTINE
                )
        ).orElseThrow();

        List<WarehouseStockLedger> stockLedgers = stockLedgerRepository.findAll();
        List<WarehouseReservationLedger> reservationLedgers = reservationLedgerRepository.findAll();

        assertThat(stockLedgers).hasSize(5);
        assertThat(reservationLedgers)
                .extracting(WarehouseReservationLedger::getMovementType)
                .containsExactlyInAnyOrder(StockLedgerMovementType.RESERVE, StockLedgerMovementType.RELEASE);
        assertThat(availableBalance.getQtyOnHand()).isEqualByComparingTo("6.0000");
        assertThat(availableBalance.getQtyReserved()).isEqualByComparingTo("0.0000");
        assertThat(availableBalance.getAvailableQty()).isEqualByComparingTo("6.0000");
        assertThat(quarantineBalance.getQtyOnHand()).isEqualByComparingTo("2.0000");
        assertThat(quarantineBalance.getAvailableQty()).isEqualByComparingTo("0.0000");
        assertBalancesNeverNegative();
        assertThat(ledgerSum(stockLedgers, WarehouseStockStatus.AVAILABLE)).isEqualByComparingTo(
                availableBalance.getQtyOnHand()
        );
        assertThat(ledgerSum(stockLedgers, WarehouseStockStatus.QUARANTINE)).isEqualByComparingTo(
                quarantineBalance.getQtyOnHand()
        );
        assertThat(stockMovementRepository.findAll())
                .extracting(StockMovement::getSourceType)
                .contains(
                        StockMovementSourceType.PURCHASE_ORDER,
                        StockMovementSourceType.WORK_ORDER_MATERIAL_USAGE,
                        StockMovementSourceType.REPAIR_MATERIAL_RETURN,
                        StockMovementSourceType.INVENTORY_COUNT_SESSION
                );
    }

    private Warehouse warehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode("WMS-SMOKE");
        warehouse.setName("WMS Smoke Warehouse");
        warehouse.setActive(true);
        return warehouse;
    }

    private WarehouseBin bin(UUID warehouseId) {
        WarehouseBin bin = new WarehouseBin();
        bin.setWarehouseId(warehouseId);
        bin.setCode("A-01-01");
        bin.setBarcode("BC-A-01-01");
        bin.setQualityZoneType(WarehouseQualityZoneType.STORAGE);
        bin.setAllowMixedSpareParts(true);
        bin.setAllowMixedLots(true);
        bin.setActive(true);
        return bin;
    }

    private SparePart sparePart() {
        SparePartType type = new SparePartType();
        type.setCode("WMS-SMOKE-TYPE");
        type.setName("Smoke type");
        type.setDefaultUnit("pcs");
        type = sparePartTypeRepository.saveAndFlush(type);

        SparePart part = new SparePart();
        part.setCode("SP-WMS-SMOKE");
        part.setName("Smoke spare part");
        part.setKind(InventoryItemKind.SPARE_PART);
        part.setType(type);
        part.setLegacyType("OTHER");
        part.setUnit("pcs");
        part.setMinStock(0);
        return part;
    }

    private WorkOrder workOrder(UUID warehouseId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setNumber("WO-WMS-SMOKE");
        workOrder.setTitle("WMS smoke work order");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setWarehouseId(warehouseId);
        workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
        workOrder.setType(WorkOrderType.DEFECT);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedById(UUID.randomUUID());
        workOrder.setStartedAt(Instant.now());
        return workOrder;
    }

    private WorkOrderSparePartRequirement requirement(WorkOrder workOrder, SparePart sparePart) {
        WorkOrderSparePartRequirement requirement = new WorkOrderSparePartRequirement();
        requirement.setWorkOrder(workOrder);
        requirement.setSourceType(WorkOrderSparePartRequirementSourceType.MANUAL);
        requirement.setSparePart(sparePart);
        requirement.setRequiredQty(4);
        requirement.setUnit("pcs");
        requirement.setStatus(WorkOrderSparePartRequirementStatus.RESERVED);
        return requirement;
    }

    private WarehouseTask pickTask(UUID warehouseId, UUID workOrderId, UUID sparePartId, UUID binId) {
        WarehouseTask task = new WarehouseTask();
        task.setTaskNumber("WT-WMS-SMOKE");
        task.setTaskType(WarehouseTaskType.PICK);
        task.setStatus(WarehouseTaskStatus.IN_PROGRESS);
        task.setPriority(WarehouseTaskPriority.NORMAL);
        task.setWarehouseId(warehouseId);
        task.setSourceType(WarehouseTaskSourceType.WORK_ORDER);
        task.setSourceId(workOrderId);

        WarehouseTaskLine line = new WarehouseTaskLine();
        line.setTask(task);
        line.setSparePartId(sparePartId);
        line.setFromBinId(binId);
        line.setLotNumber(LOT);
        line.setSerialNumber(SERIAL);
        line.setExpiryDate(EXPIRY);
        line.setStockStatus(WarehouseStockStatus.AVAILABLE);
        line.setPlannedQty(new BigDecimal("4.0000"));
        line.setActualQty(BigDecimal.ZERO);
        line.setUnit("pcs");
        line.setStatus(WarehouseTaskLineStatus.IN_PROGRESS);
        task.getLines().add(line);
        return task;
    }

    private InventoryCountSession countSession(UUID warehouseId, UUID binId) {
        InventoryCountSession session = new InventoryCountSession();
        session.setSessionNumber("IC-WMS-SMOKE");
        session.setWarehouseId(warehouseId);
        session.setStatus(InventoryCountSessionStatus.APPROVED);
        session.setScopeType(InventoryCountScopeType.BIN);
        session.setScopeBinId(binId);
        session.setBlindCount(false);
        session.setCreatedById(UUID.randomUUID());
        session.setApprovedById(UUID.randomUUID());
        session.setOpenedAt(Instant.now());
        session.setClosedAt(Instant.now());
        session.setDocumentNumber("IC-DOC-1");
        return session;
    }

    private InventoryCountLine countLine(UUID sessionId, UUID warehouseId, UUID binId, UUID sparePartId) {
        InventoryCountLine line = new InventoryCountLine();
        line.setSessionId(sessionId);
        line.setWarehouseId(warehouseId);
        line.setBinId(binId);
        line.setSparePartId(sparePartId);
        line.setLotNumber(LOT);
        line.setSerialNumber(SERIAL);
        line.setExpiryDate(EXPIRY);
        line.setStockStatus(WarehouseStockStatus.AVAILABLE);
        line.setExpectedQty(new BigDecimal("8.0000"));
        line.setCountedQty(new BigDecimal("6.0000"));
        line.setVarianceQty(new BigDecimal("-2.0000"));
        line.setUnit("pcs");
        line.setStatus(InventoryCountLineStatus.POSTED);
        line.setCountedById(UUID.randomUUID());
        line.setCountedAt(Instant.now());
        line.setVarianceReason("smoke variance");
        return line;
    }

    private StockMovement stockMovement(UUID warehouseId,
                                        UUID sparePartId,
                                        UUID binId,
                                        StockMovementType type,
                                        StockMovementSourceType sourceType,
                                        UUID sourceId,
                                        BigDecimal quantity,
                                        WarehouseStockStatus stockStatus,
                                        String documentNumber) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(warehouseId);
        movement.setSparePartId(sparePartId);
        movement.setBinId(binId);
        movement.setType(type);
        movement.setQuantity(quantity.doubleValue());
        movement.setUnit("pcs");
        movement.setDocumentNumber(documentNumber);
        movement.setSourceType(sourceType);
        movement.setSourceId(sourceId);
        movement.setMovementDate(LocalDate.now());
        movement.setOccurredAt(Instant.now());
        movement.setLotNumber(LOT);
        movement.setSerialNumber(SERIAL);
        movement.setExpiryDate(EXPIRY);
        movement.setStockStatus(stockStatus);
        return movement;
    }

    private BigDecimal ledgerSum(List<WarehouseStockLedger> ledgers, WarehouseStockStatus status) {
        return ledgers.stream()
                .filter(ledger -> ledger.getStockStatus() == status)
                .map(WarehouseStockLedger::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void assertBalancesNeverNegative() {
        assertThat(balanceRepository.findAll()).allSatisfy(balance -> {
            assertThat(balance.getQtyOnHand()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(balance.getQtyReserved()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(balance.getQtyReserved()).isLessThanOrEqualTo(balance.getQtyOnHand());
        });
    }
}
