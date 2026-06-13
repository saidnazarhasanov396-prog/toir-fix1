package com.toir.repository;

import com.toir.entity.SparePart;
import com.toir.entity.SparePartType;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.InventoryItemKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SparePartRepositoryFilterTest {

    @Autowired
    SparePartRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void warehouseIdFilterExcludesDeletedStockAndDeletedSpareParts() {
        Warehouse warehouseA = saveWarehouse("WH-A", "Warehouse A");
        Warehouse warehouseB = saveWarehouse("WH-B", "Warehouse B");

        SparePart visible = saveSparePart("SP-A", "Visible part", InventoryItemKind.SPARE_PART, false);
        SparePart inOtherWarehouse = saveSparePart("SP-B", "Other warehouse", InventoryItemKind.SPARE_PART, false);
        SparePart deletedPart = saveSparePart("SP-C", "Deleted part", InventoryItemKind.SPARE_PART, true);
        SparePart onlyDeletedStock = saveSparePart("SP-D", "Deleted stock only", InventoryItemKind.SPARE_PART, false);

        saveStock(warehouseA, visible, 10, false);
        saveStock(warehouseB, inOtherWarehouse, 10, false);
        saveStock(warehouseA, deletedPart, 10, false);
        saveStock(warehouseA, onlyDeletedStock, 10, true);

        Page<SparePart> result = repository.findAllByFilterAndWarehouseId(
                null,
                null,
                null,
                warehouseA.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).extracting(SparePart::getId)
                .containsExactly(visible.getId());
    }

    @Test
    void warehouseIdAndItemTypeReturnIntersection() {
        Warehouse warehouse = saveWarehouse("WH-I", "Intersection");
        SparePart material = saveSparePart("SP-MAT", "Material part", InventoryItemKind.MATERIAL, false);
        SparePart sparePart = saveSparePart("SP-SP", "Spare part", InventoryItemKind.SPARE_PART, false);

        saveStock(warehouse, material, 5, false);
        saveStock(warehouse, sparePart, 5, false);

        Page<SparePart> result = repository.findAllByFilterAndWarehouseId(
                InventoryItemKind.MATERIAL,
                null,
                null,
                warehouse.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).extracting(SparePart::getId)
                .containsExactly(material.getId());
    }

    @Test
    void searchAndWarehouseIdWorkTogether() {
        Warehouse warehouse = saveWarehouse("WH-S", "Search");
        SparePart bolt = saveSparePart("SP-BOLT-001", "Hex Bolt", InventoryItemKind.SPARE_PART, false);
        SparePart nut = saveSparePart("SP-NUT-001", "Hex Nut", InventoryItemKind.SPARE_PART, false);

        saveStock(warehouse, bolt, 3, false);
        saveStock(warehouse, nut, 3, false);

        Page<SparePart> result = repository.findAllByFilterAndWarehouseId(
                null,
                null,
                "%bolt%",
                warehouse.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).extracting(SparePart::getId)
                .containsExactly(bolt.getId());
    }

    @Test
    void warehouseIdsScopeFilterReturnsOnlyScopedWarehouses() {
        Warehouse warehouseA = saveWarehouse("WH-SA", "Scope A");
        Warehouse warehouseB = saveWarehouse("WH-SB", "Scope B");

        SparePart allowed = saveSparePart("SP-ALLOWED", "Allowed", InventoryItemKind.SPARE_PART, false);
        SparePart blocked = saveSparePart("SP-BLOCKED", "Blocked", InventoryItemKind.SPARE_PART, false);
        SparePart deletedScopedStock = saveSparePart("SP-DEL", "Deleted scoped stock", InventoryItemKind.SPARE_PART, false);

        saveStock(warehouseA, allowed, 4, false);
        saveStock(warehouseB, blocked, 4, false);
        saveStock(warehouseA, deletedScopedStock, 4, true);
        saveStock(warehouseB, deletedScopedStock, 4, false);

        Page<SparePart> result = repository.findAllByFilterAndWarehouseIds(
                null,
                null,
                null,
                List.of(warehouseA.getId()),
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).extracting(SparePart::getId)
                .containsExactly(allowed.getId());
    }

    private Warehouse saveWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
        return entityManager.persistAndFlush(warehouse);
    }

    private SparePart saveSparePart(String code, String name, InventoryItemKind kind, boolean deleted) {
        SparePart sparePart = new SparePart();
        sparePart.setCode(code);
        sparePart.setName(name);
        sparePart.setKind(kind);
        sparePart.setUnit("PCS");
        sparePart.setType(saveSparePartType("TYPE-" + code));
        sparePart.setLegacyType("OTHER");
        sparePart.setMinStock(0);
        sparePart.setDeleted(deleted);
        return entityManager.persistAndFlush(sparePart);
    }

    private SparePartType saveSparePartType(String code) {
        SparePartType type = new SparePartType();
        type.setCode(code);
        type.setName(code);
        type.setDefaultUnit("PCS");
        type.setActive(true);
        return entityManager.persistAndFlush(type);
    }

    private WarehouseStock saveStock(Warehouse warehouse, SparePart sparePart, double quantity, boolean deleted) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouse.getId());
        stock.setSparePart(sparePart);
        stock.setQuantity(quantity);
        stock.setReservedQty(0);
        stock.setMinQty(0);
        stock.setDeleted(deleted);
        return entityManager.persistAndFlush(stock);
    }
}
