package com.toir.controller;

import com.toir.exception.RestException;
import com.toir.entity.Department;
import com.toir.repository.DepartmentRepository;
import com.toir.enums.DepartmentType;
import com.toir.enums.InventoryItemKind;
import com.toir.entity.SparePart;
import com.toir.repository.SparePartRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Inbound integrations: Ð¿Ñ€Ð¸Ñ‘Ð¼ÐºÐ° ÑÐ¿Ñ€Ð°Ð²Ð¾Ñ‡Ð½Ð¸ÐºÐ¾Ð² Ð¸Ð· 1Ð¡ / ERP.
 * ÐŸÐ¾ Ð¢Ð— Â§4.2.14 â€” upsert Ð½Ð¾Ð¼ÐµÐ½ÐºÐ»Ð°Ñ‚ÑƒÑ€Ñ‹ Ð¸ Ð¿Ð¾Ð´Ñ€Ð°Ð·Ð´ÐµÐ»ÐµÐ½Ð¸Ð¹ Ð¿Ð¾ ÐºÐ¾Ð´Ñƒ.
 * Ð˜Ð´ÐµÐ¼Ð¿Ð¾Ñ‚ÐµÐ½Ñ‚Ð½Ñ‹Ð¹: ÑÑƒÑ‰ÐµÑÑ‚Ð²ÑƒÑŽÑ‰Ð¸Ðµ Ð·Ð°Ð¿Ð¸ÑÐ¸ Ð¾Ð±Ð½Ð¾Ð²Ð»ÑÑŽÑ‚ÑÑ, Ð½Ð¾Ð²Ñ‹Ðµ ÑÐ¾Ð·Ð´Ð°ÑŽÑ‚ÑÑ.
 */
@RestController
@RequestMapping("/api/v1/integrations/inbound")
@Tag(name = "inbound-integrations")
@Transactional
public class InboundErpController {

    private final SparePartRepository sparePartRepository;
    private final DepartmentRepository departmentRepository;

    public InboundErpController(SparePartRepository sparePartRepository,
                                DepartmentRepository departmentRepository) {
        this.sparePartRepository = sparePartRepository;
        this.departmentRepository = departmentRepository;
    }

    @PostMapping("/spare-parts")
    public UpsertResult upsertSpareParts(@Valid @RequestBody List<SparePartImport> items) {
        int created = 0;
        int updated = 0;
        for (SparePartImport item : items) {
            SparePart existing = sparePartRepository.findAll().stream()
                    .filter(sp -> item.code.equals(sp.getCode()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                SparePart sp = new SparePart();
                sp.setCode(item.code);
                sp.setName(item.name);
                sp.setSku(item.sku);
                sp.setKind(parseKind(item.kind));
                sp.setUnit(item.unit != null ? item.unit : "ÑˆÑ‚");
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

    @PostMapping("/departments")
    public UpsertResult upsertDepartments(@Valid @RequestBody List<DepartmentImport> items) {
        int created = 0;
        int updated = 0;
        for (DepartmentImport item : items) {
            Department existing = departmentRepository.findAll().stream()
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
