package com.toir.dto.warehouse;

import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.LocationRepository;

import java.util.List;
import java.util.UUID;

public record WarehouseDto(
        UUID id,
        String code,
        String name,
        UUID departmentId,
        UUID locationId,
        UUID responsibleId,
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
        return new WarehouseDto(w.getId(), w.getCode(), w.getName(),
                w.getDepartmentId(), w.getLocationId(), w.getResponsibleId(), w.isActive(),
                null, null, null,
                new Summary(0, 0, 0, 0),
                List.of());
    }

    public static WarehouseDto fromWithStocks(Warehouse w, List<WarehouseStock> stocks) {
        double totalQty = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
        double totalReserved = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
        long lowStock = stocks.stream().filter(s -> s.getQuantity() <= s.getMinQty()).count();
        return new WarehouseDto(
                w.getId(), w.getCode(), w.getName(),
                w.getDepartmentId(), w.getLocationId(), w.getResponsibleId(), w.isActive(),
                null, null, null,
                new Summary(stocks.size(), totalQty, totalReserved, (int) lowStock),
                stocks.stream().map(WarehouseStockDto::from).toList()
        );
    }

    public static WarehouseDto fromWithStocks(Warehouse w, List<WarehouseStock> stocks,
                                             DepartmentRepository departmentRepository,
                                             LocationRepository locationRepository,
                                             EmployeeRepository employeeRepository) {
        double totalQty = stocks.stream().mapToDouble(WarehouseStock::getQuantity).sum();
        double totalReserved = stocks.stream().mapToDouble(WarehouseStock::getReservedQty).sum();
        long lowStock = stocks.stream().filter(s -> s.getQuantity() <= s.getMinQty()).count();

        DepartmentRef department = null;
        LocationRef location = null;
        ResponsibleRef responsible = null;

        if (w.getDepartmentId() != null) {
            var dept = departmentRepository.findByIdAndIsDeletedFalse(w.getDepartmentId());
            if (dept.isPresent()) {
                var d = dept.get();
                department = new DepartmentRef(d.getId(), d.getCode(), d.getName());
            }
        }

        if (w.getLocationId() != null) {
            var loc = locationRepository.findByIdAndIsDeletedFalse(w.getLocationId());
            if (loc.isPresent()) {
                var l = loc.get();
                location = new LocationRef(l.getId(), l.getCode(), l.getName());
            }
        }

        if (w.getResponsibleId() != null) {
            var emp = employeeRepository.findByIdAndIsDeletedFalse(w.getResponsibleId());
            if (emp.isPresent()) {
                var e = emp.get();
                String fullName = buildFullName(e.getLastName(), e.getFirstName(), e.getMiddleName());
                responsible = new ResponsibleRef(e.getId(), fullName);
            }
        }

        return new WarehouseDto(
                w.getId(), w.getCode(), w.getName(),
                w.getDepartmentId(), w.getLocationId(), w.getResponsibleId(), w.isActive(),
                department, location, responsible,
                new Summary(stocks.size(), totalQty, totalReserved, (int) lowStock),
                stocks.stream().map(WarehouseStockDto::from).toList()
        );
    }

    private static String buildFullName(String lastName, String firstName, String middleName) {
        StringBuilder fullName = new StringBuilder();
        if (lastName != null && !lastName.isEmpty()) {
            fullName.append(lastName);
        }
        if (firstName != null && !firstName.isEmpty()) {
            if (fullName.length() > 0) fullName.append(" ");
            fullName.append(firstName);
        }
        if (middleName != null && !middleName.isEmpty()) {
            if (fullName.length() > 0) fullName.append(" ");
            fullName.append(middleName);
        }
        return fullName.toString();
    }
}
