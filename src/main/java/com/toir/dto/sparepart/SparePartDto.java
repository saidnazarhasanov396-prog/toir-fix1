package com.toir.dto.sparepart;

import com.toir.dto.mxik.MxikRefDto;
import com.toir.dto.warehouse.WarehouseStockPolicyDto;
import com.toir.entity.SparePart;
import com.toir.entity.SparePartType;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.InventoryItemKind;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * Shape matches the React frontend's inventory catalog while exposing the new type dictionary fields.
 */
public record SparePartDto(
        UUID id,
        String entityType,
        String code,
        String name,
        String kind,
        String unit,
        String unitCode,
        String unitName,
        String manufacturer,
        String sku,
        String specification,
        double minStock,
        double currentStock,
        double reservedStock,
        double availableStock,
        int warehouseCount,
        UUID typeId,
        String typeCode,
        String typeName,
        com.toir.enums.SparePartType type,
        UUID preferredCounteragentId,
        String preferredCounteragentName,
        Integer leadTimeDays,
        BigDecimal lastPurchasePrice,
        BigDecimal averageCost,
        BigDecimal lastPurchaseCost,
        BigDecimal inventoryValue,
        CriticalityLevel criticality,
        UUID mxikId,
        MxikRefDto mxik,
        List<WarehouseStockPolicyDto> warehousePolicies
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
        this(id, entityType, code, name, kind, unit == null ? null : unit.code(), unit == null ? null : unit.code(),
                unit == null ? null : unit.name(), manufacturer, sku, specification, minStock, currentStock,
                reservedStock, availableStock, warehouseCount, null, null, null, com.toir.enums.SparePartType.OTHER,
                null, null, null, null, null, null, null, null, null, null, List.of());
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
            UnitRef unitRef
    ) {
        return from(s, currentStock, reservedStock, warehouseCount, unitRef, null);
    }

    public static SparePartDto from(
            SparePart s,
            double currentStock,
            double reservedStock,
            int warehouseCount,
            UnitRef unitRef,
            MxikRefDto mxik
    ) {
        SparePartType type = s.getType();
        String unit = s.getUnit();
        String unitCode = unitRef != null ? unitRef.code() : unit;
        String unitName = unitRef != null ? unitRef.name() : unit;
        String typeCode = type == null ? s.getLegacyType() : type.getCode();
        return new SparePartDto(
                s.getId(),
                "SPARE_PART",
                s.getCode(),
                s.getName(),
                s.getKind() != null ? s.getKind().name() : InventoryItemKind.SPARE_PART.name(),
                unit,
                unitCode,
                unitName,
                s.getManufacturer(),
                s.getSku(),
                s.getSpecification(),
                s.getMinStock(),
                currentStock,
                reservedStock,
                Math.max(0, currentStock - reservedStock),
                warehouseCount,
                type == null ? null : type.getId(),
                typeCode,
                type == null ? null : type.getName(),
                legacyType(typeCode),
                s.getPreferredCounteragentId(),
                null,
                s.getLeadTimeDays(),
                s.getLastPurchasePrice(),
                s.getAverageCost(),
                s.getLastPurchaseCost(),
                s.getInventoryValue(),
                s.getCriticality(),
                s.getMxikId(),
                mxik,
                List.of()
        );
    }

    public SparePartDto withPreferredCounteragentName(String preferredCounteragentName) {
        return new SparePartDto(id, entityType, code, name, kind, unit, unitCode, unitName, manufacturer, sku,
                specification, minStock, currentStock, reservedStock, availableStock, warehouseCount,
                typeId, typeCode, typeName, type, preferredCounteragentId, preferredCounteragentName,
                leadTimeDays, lastPurchasePrice, averageCost, lastPurchaseCost, inventoryValue, criticality,
                mxikId, mxik, warehousePolicies);
    }

    public SparePartDto withWarehousePolicies(List<WarehouseStockPolicyDto> warehousePolicies) {
        return new SparePartDto(id, entityType, code, name, kind, unit, unitCode, unitName, manufacturer, sku,
                specification, minStock, currentStock, reservedStock, availableStock, warehouseCount,
                typeId, typeCode, typeName, type, preferredCounteragentId, preferredCounteragentName,
                leadTimeDays, lastPurchasePrice, averageCost, lastPurchaseCost, inventoryValue, criticality,
                mxikId, mxik, warehousePolicies == null ? List.of() : warehousePolicies);
    }

    public static UnitRef unitRef(String unit) {
        return new UnitRef(unit, unit);
    }

    private static com.toir.enums.SparePartType legacyType(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String legacyCode = switch (code) {
            case "ELECTRICAL_PART" -> "ELECTRICAL";
            case "MECHANICAL_PART" -> "MECHANICAL";
            case "METAL" -> "RAW_MATERIAL";
            default -> code;
        };
        try {
            return com.toir.enums.SparePartType.valueOf(legacyCode);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
