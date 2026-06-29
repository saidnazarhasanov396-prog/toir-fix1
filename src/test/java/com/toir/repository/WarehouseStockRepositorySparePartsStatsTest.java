package com.toir.repository;

import com.toir.entity.Reservation;
import com.toir.entity.SparePart;
import com.toir.entity.SparePartType;
import com.toir.entity.StockMovement;
import com.toir.entity.UnitOfMeasurement;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.ReservationStatus;
import com.toir.enums.StockMovementType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class WarehouseStockRepositorySparePartsStatsTest {

    @Autowired
    WarehouseStockRepository repository;

    @Autowired
    TestEntityManager entityManager;

    private SparePartType defaultSparePartType;

    @Test
    void returnsAllFourStatsWithNoFilter() {
        Warehouse warehouseA = saveWarehouse("WH-001", "Warehouse A");
        Warehouse warehouseB = saveWarehouse("WH-002", "Warehouse B");
        SparePart partA = saveSparePart("SP-001", "Part A");
        SparePart partB = saveSparePart("SP-002", "Part B");
        SparePart partC = saveSparePart("SP-003", "Part C");

        WarehouseStock stockA = saveStock(warehouseA, partA, 5, 0, 4, null);   // not low (5 > 4)
        WarehouseStock stockB = saveStock(warehouseA, partB, 6, 0, 2, 7.0);    // low (6 <= 7)
        WarehouseStock stockC = saveStock(warehouseB, partA, 4, 3, 2, null);   // low by available=1
        saveStock(warehouseB, partC, 1, 0, 0, null);                            // trigger=0, excluded

        saveReservation(stockA, ReservationStatus.ACTIVE, false);
        saveReservation(stockB, ReservationStatus.FULFILLED, false);
        saveReservation(stockC, ReservationStatus.CANCELLED, false);
        saveReservation(stockC, ReservationStatus.ACTIVE, true);

        saveMovement(warehouseA.getId(), partA.getId(), StockMovementType.ISSUE, 6, UUID.randomUUID(), false);
        saveMovement(warehouseA.getId(), partA.getId(), StockMovementType.ISSUE, 2, null, false);
        saveMovement(warehouseA.getId(), partA.getId(), StockMovementType.RECEIPT, 100, UUID.randomUUID(), false);
        saveMovement(warehouseA.getId(), partA.getId(), StockMovementType.ISSUE, 1, UUID.randomUUID(), true);
        saveMovement(warehouseB.getId(), partB.getId(), StockMovementType.ISSUE, 4, UUID.randomUUID(), false);

        saveRepairMaterialUsage(UUID.randomUUID(), warehouseA.getId(), partA.getId(), 999);

        SparePartsWarehouseStatsProjection stats = repository.getSparePartsWarehouseStats(null, null, null, null);

        assertThat(stats.getNomenclature()).isEqualTo(3);
        assertThat(stats.getActiveReservations()).isEqualTo(1);
        assertThat(stats.getLowStockItems()).isEqualTo(2);
        assertThat(stats.getIssuedToWork()).isEqualTo(10.0);
    }

    @Test
    void filtersByWarehouseId() {
        Warehouse warehouseA = saveWarehouse("WH-010", "Warehouse A");
        Warehouse warehouseB = saveWarehouse("WH-020", "Warehouse B");
        SparePart partA = saveSparePart("SP-010", "Part A");
        SparePart partB = saveSparePart("SP-020", "Part B");

        WarehouseStock stockA = saveStock(warehouseA, partA, 3, 0, 5, null); // low
        WarehouseStock stockB = saveStock(warehouseB, partB, 2, 0, 5, null); // low

        saveReservation(stockA, ReservationStatus.ACTIVE, false);
        saveReservation(stockB, ReservationStatus.ACTIVE, false);

        saveMovement(warehouseA.getId(), partA.getId(), StockMovementType.ISSUE, 5, UUID.randomUUID(), false);
        saveMovement(warehouseB.getId(), partB.getId(), StockMovementType.ISSUE, 7, UUID.randomUUID(), false);

        SparePartsWarehouseStatsProjection stats = repository.getSparePartsWarehouseStatsByWarehouseIds(
                List.of(warehouseA.getId()), null, null, null, null);

        assertThat(stats.getNomenclature()).isEqualTo(1);
        assertThat(stats.getActiveReservations()).isEqualTo(1);
        assertThat(stats.getLowStockItems()).isEqualTo(1);
        assertThat(stats.getIssuedToWork()).isEqualTo(5.0);
    }

    @Test
    void lowStockUsesAvailableAndReorderPointFallbackMinQty() {
        Warehouse warehouse = saveWarehouse("WH-030", "Warehouse");
        SparePart partA = saveSparePart("SP-030", "Part A");
        SparePart partB = saveSparePart("SP-031", "Part B");
        SparePart partC = saveSparePart("SP-032", "Part C");
        SparePart partD = saveSparePart("SP-033", "Part D");

        saveStock(warehouse, partA, 7, 3, 5, null);  // available=4, trigger=min=5 -> low
        saveStock(warehouse, partB, 10, 1, 5, 8.0);  // available=9, trigger=8 -> not low
        saveStock(warehouse, partC, 9, 1, 5, 8.0);   // available=8, trigger=8 -> low
        saveStock(warehouse, partD, 0, 0, 0, null);  // trigger=0 -> excluded

        SparePartsWarehouseStatsProjection stats = repository.getSparePartsWarehouseStatsByWarehouseIds(
                List.of(warehouse.getId()), null, null, null, null);

        assertThat(stats.getLowStockItems()).isEqualTo(2);
    }

    @Test
    void lowStockFallsBackToSparePartMinStockWhenWarehouseThresholdIsMissing() {
        Warehouse warehouse = saveWarehouse("WH-034", "Warehouse");
        SparePart atMinimum = saveSparePart("SP-034-A", "At catalog minimum", 5);
        SparePart aboveMinimum = saveSparePart("SP-034-B", "Above catalog minimum", 5);
        SparePart noThreshold = saveSparePart("SP-034-C", "No catalog minimum", 0);

        saveStock(warehouse, atMinimum, 5, 0, 0, null);
        saveStock(warehouse, aboveMinimum, 6, 0, 0, null);
        saveStock(warehouse, noThreshold, 0, 0, 0, null);

        SparePartsWarehouseStatsProjection stats = repository.getSparePartsWarehouseStatsByWarehouseIds(
                List.of(warehouse.getId()), null, null, null, null);

        assertThat(stats.getLowStockItems()).isEqualTo(1);
    }

    @Test
    void activeReservationsCountsOnlyActiveExcludingDeletedAndFinalStatuses() {
        Warehouse warehouse = saveWarehouse("WH-040", "Warehouse");
        SparePart part = saveSparePart("SP-040", "Part");
        WarehouseStock stock = saveStock(warehouse, part, 10, 0, 2, null);

        saveReservation(stock, ReservationStatus.ACTIVE, false);
        saveReservation(stock, ReservationStatus.FULFILLED, false);
        saveReservation(stock, ReservationStatus.CANCELLED, false);
        saveReservation(stock, ReservationStatus.ACTIVE, true);

        SparePartsWarehouseStatsProjection stats = repository.getSparePartsWarehouseStatsByWarehouseIds(
                List.of(warehouse.getId()), null, null, null, null);

        assertThat(stats.getActiveReservations()).isEqualTo(1);
    }

    @Test
    void issuedToWorkSumsOnlyIssueWithNonNullWorkOrderId() {
        Warehouse warehouse = saveWarehouse("WH-050", "Warehouse");
        SparePart part = saveSparePart("SP-050", "Part");

        saveMovement(warehouse.getId(), part.getId(), StockMovementType.ISSUE, 5, UUID.randomUUID(), false);
        saveMovement(warehouse.getId(), part.getId(), StockMovementType.ISSUE, 7, null, false);
        saveMovement(warehouse.getId(), part.getId(), StockMovementType.RECEIPT, 9, UUID.randomUUID(), false);
        saveMovement(warehouse.getId(), part.getId(), StockMovementType.ISSUE, 11, UUID.randomUUID(), true);

        SparePartsWarehouseStatsProjection stats = repository.getSparePartsWarehouseStatsByWarehouseIds(
                List.of(warehouse.getId()), null, null, null, null);

        assertThat(stats.getIssuedToWork()).isEqualTo(5.0);
    }

    @Test
    void issuedToWorkDoesNotDoubleCountRepairMaterialUsages() {
        Warehouse warehouse = saveWarehouse("WH-060", "Warehouse");
        SparePart part = saveSparePart("SP-060", "Part");
        UUID workOrderId = UUID.randomUUID();

        saveMovement(warehouse.getId(), part.getId(), StockMovementType.ISSUE, 3, workOrderId, false);
        saveRepairMaterialUsage(workOrderId, warehouse.getId(), part.getId(), 20);

        SparePartsWarehouseStatsProjection stats = repository.getSparePartsWarehouseStatsByWarehouseIds(
                List.of(warehouse.getId()), null, null, null, null);

        assertThat(stats.getIssuedToWork()).isEqualTo(3.0);
    }

    @Test
    void filtersByUnitIdInGlobalStats() {
        UnitOfMeasurement kg = saveUnitOfMeasurement("KG", "Kilogram");
        saveUnitOfMeasurement("PCS", "Piece");
        Warehouse warehouse = saveWarehouse("WH-UOM-1", "Warehouse");
        SparePart kgPart = saveSparePartWithUnit("SP-KG-1", "KG Part", "KG");
        SparePart pcsPart = saveSparePartWithUnit("SP-PCS-1", "PCS Part", "PCS");
        saveStock(warehouse, kgPart, 5, 0, 0, null);
        saveStock(warehouse, pcsPart, 5, 0, 0, null);

        SparePartsWarehouseStatsProjection stats = repository.getSparePartsWarehouseStats(
                null, null, null, kg.getId());

        assertThat(stats.getNomenclature()).isEqualTo(1);
    }

    @Test
    void filtersByUnitIdWithWarehouseIds() {
        UnitOfMeasurement kg = saveUnitOfMeasurement("KG-WH", "KG-WH");
        saveUnitOfMeasurement("PCS-WH", "PCS-WH");
        Warehouse warehouseA = saveWarehouse("WH-UOM-2A", "Warehouse A");
        Warehouse warehouseB = saveWarehouse("WH-UOM-2B", "Warehouse B");
        SparePart kgPart = saveSparePartWithUnit("SP-KG-2", "KG Part", "KG-WH");
        SparePart pcsPart = saveSparePartWithUnit("SP-PCS-2", "PCS Part", "PCS-WH");
        saveStock(warehouseA, kgPart, 5, 0, 0, null);
        saveStock(warehouseB, pcsPart, 5, 0, 0, null);

        SparePartsWarehouseStatsProjection stats = repository.getSparePartsWarehouseStatsByWarehouseIds(
                List.of(warehouseA.getId(), warehouseB.getId()), null, null, null, kg.getId());

        assertThat(stats.getNomenclature()).isEqualTo(1);
    }

    @Test
    void unitIdFilterNullReturnsAllParts() {
        UnitOfMeasurement kg = saveUnitOfMeasurement("KG-ALL", "Kilogram");
        Warehouse warehouse = saveWarehouse("WH-UOM-3", "Warehouse");
        SparePart kgPart = saveSparePartWithUnit("SP-KG-3", "KG Part", "KG");
        SparePart pcsPart = saveSparePartWithUnit("SP-PCS-3", "PCS Part", "PCS");
        saveStock(warehouse, kgPart, 5, 0, 0, null);
        saveStock(warehouse, pcsPart, 5, 0, 0, null);

        SparePartsWarehouseStatsProjection stats = repository.getSparePartsWarehouseStats(
                null, null, null, null);

        assertThat(stats.getNomenclature()).isEqualTo(2);
        assertThat(kg.getId()).isNotNull();
    }

    private UnitOfMeasurement saveUnitOfMeasurement(String code, String name) {
        UnitOfMeasurement unit = new UnitOfMeasurement();
        unit.setCode(code);
        unit.setName(name);
        unit.setDeleted(false);
        return entityManager.persistAndFlush(unit);
    }

    private SparePart saveSparePartWithUnit(String code, String name, String unit) {
        SparePart sparePart = new SparePart();
        sparePart.setCode(code);
        sparePart.setName(name);
        sparePart.setKind(InventoryItemKind.SPARE_PART);
        sparePart.setType(defaultSparePartType());
        sparePart.setLegacyType("OTHER");
        sparePart.setUnit(unit);
        sparePart.setMinStock(0);
        return entityManager.persistAndFlush(sparePart);
    }

    private Warehouse saveWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
        return entityManager.persistAndFlush(warehouse);
    }

    private SparePart saveSparePart(String code, String name) {
        return saveSparePart(code, name, 0);
    }

    private SparePart saveSparePart(String code, String name, double minStock) {
        SparePart sparePart = new SparePart();
        sparePart.setCode(code);
        sparePart.setName(name);
        sparePart.setKind(InventoryItemKind.SPARE_PART);
        sparePart.setType(defaultSparePartType());
        sparePart.setLegacyType("OTHER");
        sparePart.setUnit("PCS");
        sparePart.setMinStock(minStock);
        return entityManager.persistAndFlush(sparePart);
    }

    private SparePartType defaultSparePartType() {
        if (defaultSparePartType != null) {
            return defaultSparePartType;
        }
        List<SparePartType> existing = entityManager.getEntityManager()
                .createQuery("select type from SparePartType type where type.code = :code", SparePartType.class)
                .setParameter("code", "OTHER")
                .getResultList();
        if (!existing.isEmpty()) {
            defaultSparePartType = existing.getFirst();
            return defaultSparePartType;
        }

        SparePartType type = new SparePartType();
        type.setCode("OTHER");
        type.setName("Other");
        type.setDefaultUnit("PCS");
        type.setActive(true);
        defaultSparePartType = entityManager.persistAndFlush(type);
        return defaultSparePartType;
    }

    private WarehouseStock saveStock(Warehouse warehouse,
                                     SparePart sparePart,
                                     double quantity,
                                     double reservedQty,
                                     double minQty,
                                     Double reorderPoint) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouse.getId());
        stock.setSparePart(sparePart);
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        stock.setMinQty(minQty);
        stock.setReorderPoint(reorderPoint);
        stock.setReorderQty(null);
        return entityManager.persistAndFlush(stock);
    }

    private Reservation saveReservation(WarehouseStock stock, ReservationStatus status, boolean deleted) {
        Reservation reservation = new Reservation();
        reservation.setWarehouseStockId(stock.getId());
        reservation.setWorkOrderId(UUID.randomUUID());
        reservation.setReservedById(UUID.randomUUID());
        reservation.setQuantity(1);
        reservation.setStatus(status);
        reservation.setDeleted(deleted);
        return entityManager.persistAndFlush(reservation);
    }

    private StockMovement saveMovement(UUID warehouseId,
                                       UUID sparePartId,
                                       StockMovementType type,
                                       double quantity,
                                       UUID workOrderId,
                                       boolean deleted) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(warehouseId);
        movement.setSparePartId(sparePartId);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setWorkOrderId(workOrderId);
        movement.setDeleted(deleted);
        return entityManager.persistAndFlush(movement);
    }

    private RepairMaterialUsage saveRepairMaterialUsage(UUID workOrderId, UUID warehouseId, UUID sparePartId, double quantity) {
        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setWorkOrderId(workOrderId);
        usage.setWarehouseId(warehouseId);
        usage.setSparePartId(sparePartId);
        usage.setQuantity(quantity);
        return entityManager.persistAndFlush(usage);
    }
}
