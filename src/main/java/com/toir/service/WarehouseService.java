package com.toir.service;

import com.toir.dto.warehouse.WarehouseDto;
import com.toir.dto.warehouse.WarehouseRequest;
import com.toir.dto.warehouse.WarehouseStockDto;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.LocationRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository repository;
    private final WarehouseStockRepository stockRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditBuilderService auditBuilderService;



    @Transactional(readOnly = true)
    public List<WarehouseDto> findAll(String search, UUID departmentId, UUID locationId, UUID responsibleId, Boolean active) {
        return repository.search(normalizeSearch(search), departmentId, locationId, responsibleId, active).stream()
                .map(w -> WarehouseDto.fromWithStocks(w, stockRepository.findAllByWarehouseIdAndIsDeletedFalse(w.getId()),
                        departmentRepository, locationRepository, employeeRepository))
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseDto findById(UUID id) {
        Warehouse w = getOrThrow(id);
        return WarehouseDto.fromWithStocks(w, stockRepository.findAllByWarehouseIdAndIsDeletedFalse(id),
                departmentRepository, locationRepository, employeeRepository);
    }

    @Transactional(readOnly = true)
    public List<WarehouseStockDto> findStocks(UUID warehouseId) {
        getOrThrow(warehouseId);
        return stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId).stream().map(WarehouseStockDto::from).toList();
    }

    @Transactional
    public WarehouseDto create(WarehouseRequest request) {
        Warehouse entity = new Warehouse();
        entity.setCode(nextCode());
        apply(entity, request);
        Warehouse saved = repository.save(entity);

        auditBuilderService.log(
                "warehouse",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.WAREHOUSE,
                "Склад создан",
                null,
                saved
        );

        return WarehouseDto.fromWithStocks(saved, List.of(), departmentRepository, locationRepository, employeeRepository);
    }

    @Transactional
    public WarehouseDto update(UUID id, WarehouseRequest request) {
        Warehouse entity = getOrThrow(id);
        apply(entity, request);
        Warehouse updated = repository.save(entity);

        auditBuilderService.log(
                "warehouse",
                updated.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.WAREHOUSE,
                "Склад обновлен",
                entity,
                updated
        );

        return WarehouseDto.fromWithStocks(updated, stockRepository.findAllByWarehouseIdAndIsDeletedFalse(id),
                departmentRepository, locationRepository, employeeRepository);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        Warehouse saved = repository.save(entity);

        auditBuilderService.log(
                "warehouse",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.WAREHOUSE,
                "Склад удален",
                saved,
                null
        );

    }

    Warehouse getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Warehouse not found: " + id));
    }

    private void apply(Warehouse entity, WarehouseRequest request) {
        entity.setName(request.name());
        entity.setDepartmentId(request.departmentId());
        entity.setLocationId(request.locationId());
        entity.setResponsibleId(request.responsibleId());
        if (request.active() != null) entity.setActive(request.active());
    }

    private String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "WH-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("WH", year, sequence);
        while (repository.existsByCode(code)) {
            sequence++;
            code = formatCode("WH", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

}
