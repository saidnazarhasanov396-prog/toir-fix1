package com.toir.dto.sparepart;

import com.toir.entity.InventoryItemKind;
import com.toir.entity.SparePart;

import java.util.UUID;

/**
 * Shape matches the React frontend's {@code InventoryCatalogItem} so the spare-parts page
 * can render directly from {@code GET /spare-parts}.
 */
public record SparePartDto(
        UUID id,
        String entityType,
        String code,
        String name,
        String kind,
        UnitRef unit,
        String manufacturer,
        String sku,
        String specification,
        double minStock,
        double currentStock,
        double reservedStock,
        double availableStock,
        int warehouseCount
) {
    public record UnitRef(String code, String name) {}

    public static SparePartDto from(SparePart s) {
        return from(s, 0, 0, 0);
    }

    public static SparePartDto from(SparePart s, double currentStock, double reservedStock, int warehouseCount) {
        return new SparePartDto(
                s.getId(),
                "SPARE_PART",
                s.getCode(),
                s.getName(),
                s.getKind() != null ? s.getKind().name() : InventoryItemKind.SPARE_PART.name(),
                new UnitRef(s.getUnit(), s.getUnit()),
                s.getManufacturer(),
                s.getSku(),
                s.getSpecification(),
                s.getMinStock(),
                currentStock,
                reservedStock,
                Math.max(0, currentStock - reservedStock),
                warehouseCount
        );
    }
}
