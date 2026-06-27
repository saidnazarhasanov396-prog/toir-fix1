package com.toir.service.equipment;

import com.toir.dto.equipment.WarrantyReportItem;
import com.toir.entity.Department;
import com.toir.entity.Supplier;
import com.toir.entity.equipment.Equipment;
import com.toir.repository.SupplierRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarrantyReportService {

    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final SupplierRepository supplierRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public Page<WarrantyReportItem> report(
            UUID departmentId,
            String status,
            UUID supplierId,
            String search,
            int page,
            int size
    ) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Pageable pageable = PageRequest.of(page, size);

        List<Equipment> all = equipmentRepository.findAllActiveWithWarranty(departmentId, supplierId, search);

        if (!scopeAccessService.isScopeAdmin()) {
            UUID currentDept = scopeAccessService.currentDepartmentIdOrNull();
            if (currentDept != null) {
                all = all.stream()
                        .filter(e -> currentDept.equals(e.getResponsibleDepartmentId())
                                || currentDept.equals(e.getDepartmentId()))
                        .toList();
            }
        }

        Map<UUID, String> departmentNames = loadDepartmentNames(all);
        Map<UUID, String> supplierNames = loadSupplierNames(all);

        List<WarrantyReportItem> items = all.stream()
                .map(e -> toItem(e, today, departmentNames, supplierNames))
                .filter(item -> matchesStatus(item, status))
                .sorted(Comparator.comparingLong(WarrantyReportItem::daysUntilExpiry))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());
        List<WarrantyReportItem> pageContent = start >= items.size()
                ? List.of()
                : items.subList(start, end);

        return new PageImpl<>(pageContent, pageable, items.size());
    }

    private Map<UUID, String> loadDepartmentNames(List<Equipment> equipment) {
        Set<UUID> departmentIds = equipment.stream()
                .map(e -> e.getResponsibleDepartmentId() != null
                        ? e.getResponsibleDepartmentId()
                        : e.getDepartmentId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (departmentIds.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllByIdInAndIsDeletedFalse(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
    }

    private Map<UUID, String> loadSupplierNames(List<Equipment> equipment) {
        Set<UUID> supplierIds = equipment.stream()
                .map(Equipment::getWarrantySupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        return supplierRepository.findAllByIdInAndIsDeletedFalse(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
    }

    private WarrantyReportItem toItem(
            Equipment e,
            LocalDate today,
            Map<UUID, String> departmentNames,
            Map<UUID, String> supplierNames
    ) {
        LocalDate end = e.getWarrantyEndDate() != null ? e.getWarrantyEndDate() : e.getWarrantyUntil();
        long daysLeft = end != null ? ChronoUnit.DAYS.between(today, end) : Long.MAX_VALUE;
        String warrantyStatus;
        if (!Boolean.TRUE.equals(e.getHasWarranty()) || end == null) {
            warrantyStatus = "NO_WARRANTY";
        } else if (daysLeft < 0) {
            warrantyStatus = "EXPIRED";
        } else if (daysLeft <= 7) {
            warrantyStatus = "EXPIRING_7";
        } else if (daysLeft <= 30) {
            warrantyStatus = "EXPIRING_30";
        } else {
            warrantyStatus = "ACTIVE";
        }
        UUID deptId = e.getResponsibleDepartmentId() != null
                ? e.getResponsibleDepartmentId()
                : e.getDepartmentId();
        return new WarrantyReportItem(
                e.getId(),
                e.getCode(),
                e.getName(),
                deptId != null ? departmentNames.get(deptId) : null,
                Boolean.TRUE.equals(e.getHasWarranty()),
                e.getWarrantyStartDate(),
                end,
                warrantyStatus,
                daysLeft,
                e.getWarrantySupplierId(),
                e.getWarrantySupplierId() != null ? supplierNames.get(e.getWarrantySupplierId()) : null
        );
    }

    private boolean matchesStatus(WarrantyReportItem item, String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return true;
        }
        return status.equalsIgnoreCase(item.warrantyStatus());
    }
}
