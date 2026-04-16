package com.toir.warehouse.dto;

import com.toir.warehouse.Warehouse;
import com.toir.warehouse.WarehouseStock;

import java.util.List;
import java.util.UUID;

public record WarehouseDto(
        UUID id,
        String code,
        String name,
        boolean isActive,
        DepartmentRef department,
        LocationRef location,
        ResponsibleRef responsible,
        Summary summary,
        List<WarehouseStockDto> stocks
) {
    public record DepartmentRef(UUID id, String code, String name) {}
    public record LocationRef(UUID id, String code, String name) {}
    public record ResponsibleRef(UUID id, String fullName) {}
    public record Summary(int totalItems, double totalQuantity, double totalReserved, int lowStockItems) {}

    public static WarehouseDto from(Warehouse w) {
        return new WarehouseDto(w.getId(), w.getCode(), w.getName(), w.isActive(),
                null, null, null,
                new Summary(0, 0, 0, 0),
                List.of());
    }

    public static WarehouseDto fromWithStocks(Warehouse w, List<WarehouseStock> stocks) {
        double totalQty = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
        double totalReserved = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
        long lowStock = stocks.stream().filter(s -> s.getQuantity() <= s.getMinQty()).count();
        return new WarehouseDto(
                w.getId(), w.getCode(), w.getName(), w.isActive(),
                null, null, null,
                new Summary(stocks.size(), totalQty, totalReserved, (int) lowStock),
                stocks.stream().map(WarehouseStockDto::from).toList()
        );
    }
}
