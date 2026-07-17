package com.toir.repository;

import com.toir.entity.SparePart;
import com.toir.entity.SparePartType;
import com.toir.entity.StockMovement;
import com.toir.entity.UnitOfMeasurement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.users.Employee;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.StockMovementType;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.test.RepositorySliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
class StockMovementRepositoryIssuedToWorkTest {

    @Autowired
    StockMovementRepository movementRepository;

    @Autowired
    WarehouseStockRepository stockRepository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void detailContainsOnlyKpiContributorsAndReconcilesWithIssuedTotal() {
        Warehouse warehouse = saveWarehouse("WH-ISSUE-1", "Issue warehouse");
        SparePart includedPart = saveSparePart("SP-ISSUE-1", "Hydraulic bolt", "PCS", false);
        SparePart deletedPart = saveSparePart("SP-ISSUE-DELETED", "Deleted bolt", "PCS", true);
        WorkOrder workOrder = saveWorkOrder("WO-ISSUE-1", "Repair hydraulic unit");
        Employee responsible = saveEmployee("EMP-ISSUE-1", "Aziza", "Karimova");

        StockMovement included = saveMovement(
                warehouse, includedPart, workOrder.getId(), StockMovementType.ISSUE, "5.2500",
                LocalDate.of(2026, 7, 15), false);
        included.setResponsiblePersonId(responsible.getId());
        entityManager.persistAndFlush(included);
        saveMovement(warehouse, includedPart, workOrder.getId(), StockMovementType.RECEIPT, "100", LocalDate.of(2026, 7, 16), false);
        saveMovement(warehouse, includedPart, null, StockMovementType.ISSUE, "7", LocalDate.of(2026, 7, 16), false);
        saveMovement(warehouse, includedPart, workOrder.getId(), StockMovementType.ISSUE, "11", LocalDate.of(2026, 7, 16), true);
        saveMovement(warehouse, deletedPart, workOrder.getId(), StockMovementType.ISSUE, "13", LocalDate.of(2026, 7, 16), false);

        Page<IssuedToWorkRowProjection> detail = movementRepository.findIssuedToWorkByWarehouseIds(
                List.of(warehouse.getId()), null, null, null, null, PageRequest.of(0, 20));
        SparePartsWarehouseStatsProjection stats = stockRepository.getSparePartsWarehouseStatsByWarehouseIds(
                List.of(warehouse.getId()), null, null, null, null);

        assertThat(detail.getContent()).singleElement().satisfies(row -> {
            assertThat(row.getMovementId()).isEqualTo(included.getId());
            assertThat(row.getMovementDate()).isEqualTo(LocalDate.of(2026, 7, 15));
            assertThat(row.getSparePartCode()).isEqualTo("SP-ISSUE-1");
            assertThat(row.getSparePartName()).isEqualTo("Hydraulic bolt");
            assertThat(row.getQuantity()).isEqualByComparingTo("5.2500");
            assertThat(row.getUnit()).isEqualTo("PCS");
            assertThat(row.getWarehouseName()).isEqualTo("Issue warehouse");
            assertThat(row.getWorkOrderId()).isEqualTo(workOrder.getId());
            assertThat(row.getWorkOrderNumber()).isEqualTo("WO-ISSUE-1");
            assertThat(row.getWorkOrderTitle()).isEqualTo("Repair hydraulic unit");
            assertThat(row.getWorkOrderStatus()).isEqualTo("IN_PROGRESS");
            assertThat(row.getResponsiblePersonName()).isEqualTo("Aziza Karimova");
        });
        assertThat(detail.stream().map(IssuedToWorkRowProjection::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(BigDecimal.valueOf(stats.getIssuedToWork()));
    }

    @Test
    void detailAppliesWarehouseSearchTypeItemTypeAndUnitFilters() {
        UnitOfMeasurement kg = saveUnit("KG", "Kilogram");
        Warehouse warehouseA = saveWarehouse("WH-ISSUE-2A", "Warehouse A");
        Warehouse warehouseB = saveWarehouse("WH-ISSUE-2B", "Warehouse B");
        SparePartType targetType = saveType("BEARING", "Bearing");
        SparePart matching = saveSparePart("SP-MATCH", "Main bearing", "KG", false, targetType, InventoryItemKind.SPARE_PART);
        SparePart otherType = saveSparePart("SP-OTHER", "Main bearing material", "KG", false, targetType, InventoryItemKind.MATERIAL);
        WorkOrder workOrder = saveWorkOrder("WO-ISSUE-2", "Bearing repair");
        StockMovement expected = saveMovement(warehouseA, matching, workOrder.getId(), StockMovementType.ISSUE, "2", LocalDate.of(2026, 7, 14), false);
        saveMovement(warehouseA, otherType, workOrder.getId(), StockMovementType.ISSUE, "3", LocalDate.of(2026, 7, 14), false);
        saveMovement(warehouseB, matching, workOrder.getId(), StockMovementType.ISSUE, "4", LocalDate.of(2026, 7, 14), false);

        Page<IssuedToWorkRowProjection> result = movementRepository.findIssuedToWorkByWarehouseIds(
                List.of(warehouseA.getId()), "bearing", targetType.getId(), "SPARE_PART", kg.getId(),
                PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(IssuedToWorkRowProjection::getMovementId)
                .containsExactly(expected.getId());
    }

    @Test
    void globalDetailUsesStableMovementDateAndIdOrderingAcrossPages() {
        Warehouse warehouse = saveWarehouse("WH-ISSUE-3", "Warehouse");
        SparePart part = saveSparePart("SP-ISSUE-3", "Part", "PCS", false);
        WorkOrder workOrder = saveWorkOrder("WO-ISSUE-3", "Repair");
        StockMovement oldest = saveMovement(warehouse, part, workOrder.getId(), StockMovementType.ISSUE, "1", LocalDate.of(2026, 7, 10), false);
        StockMovement sameDateFirst = saveMovement(warehouse, part, workOrder.getId(), StockMovementType.ISSUE, "2", LocalDate.of(2026, 7, 11), false);
        StockMovement sameDateSecond = saveMovement(warehouse, part, workOrder.getId(), StockMovementType.ISSUE, "3", LocalDate.of(2026, 7, 11), false);

        Page<IssuedToWorkRowProjection> completePage = movementRepository.findIssuedToWork(
                null, null, null, null, PageRequest.of(0, 3));
        Page<IssuedToWorkRowProjection> firstPage = movementRepository.findIssuedToWork(
                null, null, null, null, PageRequest.of(0, 2));
        Page<IssuedToWorkRowProjection> secondPage = movementRepository.findIssuedToWork(
                null, null, null, null, PageRequest.of(1, 2));

        List<UUID> pagedIds = java.util.stream.Stream.concat(firstPage.stream(), secondPage.stream())
                .map(IssuedToWorkRowProjection::getMovementId)
                .toList();
        assertThat(pagedIds).containsExactlyElementsOf(
                completePage.stream().map(IssuedToWorkRowProjection::getMovementId).toList());
        assertThat(pagedIds.subList(0, 2)).containsExactlyInAnyOrder(
                sameDateFirst.getId(), sameDateSecond.getId());
        assertThat(pagedIds.get(2)).isEqualTo(oldest.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
    }

    private Warehouse saveWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
        return entityManager.persistAndFlush(warehouse);
    }

    private SparePart saveSparePart(String code, String name, String unit, boolean deleted) {
        return saveSparePart(code, name, unit, deleted, saveType(code + "-TYPE", name + " type"), InventoryItemKind.SPARE_PART);
    }

    private SparePart saveSparePart(String code,
                                    String name,
                                    String unit,
                                    boolean deleted,
                                    SparePartType type,
                                    InventoryItemKind kind) {
        SparePart part = new SparePart();
        part.setCode(code);
        part.setName(name);
        part.setUnit(unit);
        part.setType(type);
        part.setLegacyType("OTHER");
        part.setKind(kind);
        part.setDeleted(deleted);
        return entityManager.persistAndFlush(part);
    }

    private SparePartType saveType(String code, String name) {
        SparePartType type = new SparePartType();
        type.setCode(code);
        type.setName(name);
        type.setDefaultUnit("PCS");
        type.setActive(true);
        return entityManager.persistAndFlush(type);
    }

    private UnitOfMeasurement saveUnit(String code, String name) {
        UnitOfMeasurement unit = new UnitOfMeasurement();
        unit.setCode(code);
        unit.setName(name);
        return entityManager.persistAndFlush(unit);
    }

    private WorkOrder saveWorkOrder(String number, String title) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setNumber(number);
        workOrder.setTitle(title);
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
        return entityManager.persistAndFlush(workOrder);
    }

    private Employee saveEmployee(String personnelNumber, String firstName, String lastName) {
        Employee employee = new Employee();
        employee.setPersonnelNumber(personnelNumber);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setPosition("Mechanic");
        employee.setHireDate(LocalDate.of(2025, 1, 1));
        employee.setActive(true);
        return entityManager.persistAndFlush(employee);
    }

    private StockMovement saveMovement(Warehouse warehouse,
                                       SparePart sparePart,
                                       UUID workOrderId,
                                       StockMovementType type,
                                       String quantity,
                                       LocalDate movementDate,
                                       boolean deleted) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(warehouse.getId());
        movement.setSparePartId(sparePart.getId());
        movement.setWorkOrderId(workOrderId);
        movement.setType(type);
        movement.setQuantity(new BigDecimal(quantity));
        movement.setUnit(sparePart.getUnit());
        movement.setMovementDate(movementDate);
        movement.setOccurredAt(Instant.parse(movementDate + "T10:00:00Z"));
        movement.setResponsiblePersonId(UUID.randomUUID());
        movement.setDocumentNumber("DOC-" + movementDate);
        movement.setDeleted(deleted);
        return entityManager.persistAndFlush(movement);
    }
}
