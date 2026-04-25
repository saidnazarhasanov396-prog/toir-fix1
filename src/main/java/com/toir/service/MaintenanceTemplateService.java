package com.toir.service;
import com.toir.entity.MaintenanceOperation;
import com.toir.entity.MaintenanceTemplate;
import com.toir.repository.MaintenanceOperationRepository;
import com.toir.repository.MaintenanceTemplateRepository;

import com.toir.exception.RestException;
import com.toir.dto.maintenancetemplate.MaintenanceOperationDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceTemplateService {

    private final MaintenanceTemplateRepository repository;
    private final MaintenanceOperationRepository operationRepository;


    @Transactional(readOnly = true)
    public List<MaintenanceTemplateDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(MaintenanceTemplateDto::from).toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceTemplateDto findById(UUID id) {
        return MaintenanceTemplateDto.from(getOrThrow(id));
    }

    public MaintenanceTemplateDto create(MaintenanceTemplateRequest r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Template code already exists: " + r.code());
        }
        MaintenanceTemplate t = new MaintenanceTemplate();
        apply(t, r);
        return MaintenanceTemplateDto.from(repository.save(t));
    }

    public MaintenanceTemplateDto update(UUID id, MaintenanceTemplateRequest r) {
        MaintenanceTemplate t = getOrThrow(id);
        apply(t, r);
        return MaintenanceTemplateDto.from(t);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    public MaintenanceOperationDto addOperation(UUID templateId, MaintenanceOperationDto r) {
        MaintenanceTemplate t = getOrThrow(templateId);
        MaintenanceOperation op = new MaintenanceOperation();
        op.setTemplate(t);
        op.setSequence(r.sequence());
        op.setName(r.name());
        op.setDescription(r.description());
        op.setDurationHours(r.durationHours());
        op.setRequiredSkill(r.requiredSkill());
        op.setSafetyNotes(r.safetyNotes());
        op.setToolsRequired(r.toolsRequired());
        op.setSparePartsRequired(r.sparePartsRequired());
        op.setConsumablesRequired(r.consumablesRequired());
        op.setControlParameter(r.controlParameter());
        op.setControlUnit(r.controlUnit());
        op.setControlMin(r.controlMin());
        op.setControlMax(r.controlMax());
        op.setInstructionUrl(r.instructionUrl());
        t.getOperations().add(op);
        return MaintenanceOperationDto.from(operationRepository.save(op));
    }

    public void removeOperation(UUID operationId) {
        operationRepository.findByIdAndIsDeletedFalse(operationId).ifPresent(entity -> {
            entity.setDeleted(true);
            operationRepository.save(entity);
        });
    }

    private MaintenanceTemplate getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Template not found: " + id));
    }

    private void apply(MaintenanceTemplate t, MaintenanceTemplateRequest r) {
        t.setCode(r.code());
        t.setName(r.name());
        t.setDescription(r.description());
        t.setEquipmentTypeId(r.equipmentTypeId());
        t.setMaintenanceKind(r.maintenanceKind());
        t.setNormativeLaborHours(r.normativeLaborHours());
        if (r.active() != null) t.setActive(r.active());
    }
}
