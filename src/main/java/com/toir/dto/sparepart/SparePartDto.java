package com.toir.dto.sparepart;

import com.toir.enums.InventoryItemKind;
import com.toir.entity.SparePart;
import com.toir.enums.SparePartType;

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
        int warehouseCount,
        SparePartType type
) {
    public record UnitRef(String code, String name) {}

    public SparePartDto(
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
        this(id, entityType, code, name, kind, unit, manufacturer, sku, specification, minStock,
                currentStock, reservedStock, availableStock, warehouseCount, SparePartType.OTHER);
    }

    public static SparePartDto from(SparePart s) {
        return from(s, 0, 0, 0, unitRef(s.getUnit()));
    }

    public static SparePartDto from(SparePart s, double currentStock, double reservedStock, int warehouseCount) {
        return from(s, currentStock, reservedStock, warehouseCount, unitRef(s.getUnit()));
    }

    public static SparePartDto from(
            SparePart s,
            double currentStock,
            double reservedStock,
            int warehouseCount,
            UnitRef unit
    ) {
        return new SparePartDto(
                s.getId(),
                "SPARE_PART",
                s.getCode(),
                s.getName(),
                s.getKind() != null ? s.getKind().name() : InventoryItemKind.SPARE_PART.name(),
                unit,
                s.getManufacturer(),
                s.getSku(),
                s.getSpecification(),
                s.getMinStock(),
                currentStock,
                reservedStock,
                Math.max(0, currentStock - reservedStock),
                warehouseCount,
                s.getType() != null ? s.getType() : SparePartType.OTHER
        );
    }

    public static UnitRef unitRef(String unit) {
        return new UnitRef(unit, unit);
    }
}
