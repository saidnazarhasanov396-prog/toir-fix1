package com.toir.service;

import com.toir.entity.Department;
import com.toir.entity.SparePart;
import com.toir.enums.DepartmentType;
import com.toir.enums.InventoryItemKind;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.department.DepartmentRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InboundErpService {

    private final SparePartRepository sparePartRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional
    public UpsertResult upsertSpareParts(List<SparePartImport> items) {
        int created = 0;
        int updated = 0;
        for (SparePartImport item : items) {
            SparePart existing = sparePartRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                    .filter(sp -> item.code.equals(sp.getCode()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                SparePart sp = new SparePart();
                sp.setCode(item.code);
                sp.setName(item.name);
                sp.setSku(item.sku);
                sp.setKind(parseKind(item.kind));
                sp.setUnit(item.unit != null ? item.unit : "шт");
                sp.setSpecification(item.specification);
                sp.setManufacturer(item.manufacturer);
                sp.setMinStock(item.minStock != null ? item.minStock : 0);
                sparePartRepository.save(sp);
                created++;
            } else {
                existing.setName(item.name);
                if (item.sku != null) existing.setSku(item.sku);
                if (item.unit != null) existing.setUnit(item.unit);
                if (item.specification != null) existing.setSpecification(item.specification);
                if (item.manufacturer != null) existing.setManufacturer(item.manufacturer);
                if (item.minStock != null) existing.setMinStock(item.minStock);
                updated++;
            }
        }
        return new UpsertResult(created, updated, items.size());
    }

    @Transactional
    public UpsertResult upsertDepartments(List<DepartmentImport> items) {
        int created = 0;
        int updated = 0;
        for (DepartmentImport item : items) {
            Department existing = departmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                    .filter(d -> item.code.equals(d.getCode()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                Department d = new Department();
                d.setCode(item.code);
                d.setName(item.name);
                if (item.nameEn != null) d.setNameEn(item.nameEn);
                if (item.nameUz != null) d.setNameUz(item.nameUz);
                d.setType(parseType(item.type));
                departmentRepository.save(d);
                created++;
            } else {
                existing.setName(item.name);
                if (item.nameEn != null) existing.setNameEn(item.nameEn);
                if (item.nameUz != null) existing.setNameUz(item.nameUz);
                if (item.type != null) existing.setType(parseType(item.type));
                updated++;
            }
        }
        return new UpsertResult(created, updated, items.size());
    }

    private InventoryItemKind parseKind(String kind) {
        if (kind == null) return InventoryItemKind.SPARE_PART;
        try {
            return InventoryItemKind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            return InventoryItemKind.SPARE_PART;
        }
    }

    private DepartmentType parseType(String type) {
        if (type == null) return DepartmentType.SECTION;
        try {
            return DepartmentType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw RestException.badRequest("Invalid department type: " + type);
        }
    }

    public record UpsertResult(int created, int updated, int received) {}

    public static class SparePartImport {
        @NotBlank public String code;
        @NotBlank public String name;
        public String sku;
        public String kind;
        public String unit;
        public String specification;
        public String manufacturer;
        public Double minStock;
    }

    public static class DepartmentImport {
        @NotBlank public String code;
        @NotBlank public String name;
        public String nameEn;
        public String nameUz;
        public String type;
    }
}
