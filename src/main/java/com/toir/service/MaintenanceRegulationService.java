package com.toir.service;
import com.toir.entity.MaintenanceRegulation;
import com.toir.repository.MaintenanceRegulationRepository;

import com.toir.exception.RestException;
import com.toir.util.PaginationUtils;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MaintenanceRegulationService {

    private final MaintenanceRegulationRepository repository;


    @Transactional(readOnly = true)
    public List<MaintenanceRegulationDto> findAll() {
        return com.toir.util.UpdatedAtSorter.descending(repository.findAllByIsDeletedFalse()).stream().map(MaintenanceRegulationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceRegulationDto> search(int page, int pageSize, String search) {
        var pageable = PaginationUtils.updatedAtDescPageRequest(page, pageSize);
        return repository.searchPaginated(
                search,
                pageable
        ).map(MaintenanceRegulationDto::from);
    }

    @Transactional(readOnly = true)
    public MaintenanceRegulationDto findById(UUID id) {
        return MaintenanceRegulationDto.from(getOrThrow(id));
    }

    public MaintenanceRegulation getEntityOrThrow(UUID id) {
        return getOrThrow(id);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRegulation> findActiveByEquipmentType(UUID equipmentTypeId) {
        return repository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(equipmentTypeId);
    }

    public MaintenanceRegulationDto create(MaintenanceRegulationRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Regulation code already exists: " + request.code());
        }
        MaintenanceRegulation entity = new MaintenanceRegulation();
        apply(entity, request);
        return MaintenanceRegulationDto.from(repository.save(entity));
    }

    public MaintenanceRegulationDto update(UUID id, MaintenanceRegulationRequest request) {
        MaintenanceRegulation entity = getOrThrow(id);
        apply(entity, request);
        return MaintenanceRegulationDto.from(entity);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity);
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
