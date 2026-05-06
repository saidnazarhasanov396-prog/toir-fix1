package com.toir.service.maintanance;

import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceRegulationService {

    private final MaintenanceRegulationRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<MaintenanceRegulationDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(MaintenanceRegulationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceRegulationDto> search(int page, int pageSize, String search) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        return repository.searchPaginated(
                search,
                pageable
        ).map(MaintenanceRegulationDto::from);
    }

    @Transactional(readOnly = true)
    public MaintenanceRegulationDto findById(UUID id) {
        return MaintenanceRegulationDto.from(getOrThrow(id));
    }


    @Transactional(readOnly = true)
    public List<MaintenanceRegulation> findActiveByEquipmentType(UUID equipmentTypeId) {
        return repository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(equipmentTypeId);
    }

    @Transactional
    public MaintenanceRegulationDto create(MaintenanceRegulationRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Regulation code already exists: " + request.code());
        }
        MaintenanceRegulation entity = new MaintenanceRegulation();
        apply(entity, request);
        MaintenanceRegulation saved = repository.save(entity);

        auditBuilderService.log(
                "maintenance_regulation",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.MAINTENANCE_REGULATION,
                "Регламент обслуживания создан",
                null,
                saved);

        return MaintenanceRegulationDto.from(saved);
    }

    @Transactional
    public MaintenanceRegulationDto update(UUID id, MaintenanceRegulationRequest request) {
        MaintenanceRegulation entity = getOrThrow(id);
        apply(entity, request);

        MaintenanceRegulation save = repository.save(entity);

        auditBuilderService.log(
                "maintenance_regulation",
                id != null ? id.toString() : null,
                AuditAction.UPDATE,
                AuditModule.MAINTENANCE_REGULATION,
                "Регламент обслуживания обновлен",
                entity,
                save);

        return MaintenanceRegulationDto.from(entity);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        MaintenanceRegulation saved = repository.save(entity);

        auditBuilderService.log(
                "maintenance_regulation",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.MAINTENANCE_REGULATION,
                "Регламент обслуживания удален",
                saved,
                null);

    }

    private MaintenanceRegulation getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Maintenance regulation not found: " + id));
    }

    private void apply(MaintenanceRegulation entity, MaintenanceRegulationRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setEquipmentTypeId(request.equipmentTypeId());
        entity.setMaintenanceKind(request.maintenanceKind());
        entity.setNormativeLaborHours(request.normativeLaborHours());
        if (request.active() != null) entity.setActive(request.active());
        entity.setPeriodicityUnit(request.periodicityUnit());
        entity.setPeriodicityValue(request.periodicityValue());
        entity.setToleranceDays(request.toleranceDays());
        entity.setRequiresShutdown(request.requiresShutdown());
        entity.setTriggerMeterType(request.triggerMeterType());
        entity.setTriggerMeterInterval(request.triggerMeterInterval());
    }
}
