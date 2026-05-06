package com.toir.service.maintanance;

import com.toir.dto.maintenancetemplate.MaintenanceOperationDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateRequest;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.MaintenanceKind;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.service.SparePartService;
import com.toir.util.AuditBuilderService;
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
    private final SparePartService sparePartService;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<MaintenanceTemplateDto> findAll(String search, MaintenanceKind type) {
        return repository.findAllByIsDeletedFalseAndMaintenanceKindAndSearch(sparePartService.toSearchPattern(search), type).stream()
                .map(MaintenanceTemplateDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceTemplateDto findById(UUID id) {
        return MaintenanceTemplateDto.from(getOrThrow(id));
    }

    @Transactional
    public MaintenanceTemplateDto create(MaintenanceTemplateRequest r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Template code already exists: " + r.code());
        }
        MaintenanceTemplate t = new MaintenanceTemplate();
        apply(t, r);
        MaintenanceTemplate saved = repository.save(t);

        auditBuilderService.log(
                "maintenance_template",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.MAINTENANCE_TEMPLATE,
                "Шаблон обслуживания создан",
                null,
                saved);

        return MaintenanceTemplateDto.from(saved);
    }

    @Transactional
    public MaintenanceTemplateDto update(UUID id, MaintenanceTemplateRequest r) {
        MaintenanceTemplate t = getOrThrow(id);
        apply(t, r);

        MaintenanceTemplate saved = repository.save(t);

        auditBuilderService.log(
                "maintenance_template",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.MAINTENANCE_TEMPLATE,
                "Шаблон обслуживания обновлен",
                t,
                saved);

        return MaintenanceTemplateDto.from(t);
    }

    @Transactional
    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        MaintenanceTemplate saved = repository.save(entity);

        auditBuilderService.log(
                "maintenance_template",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.MAINTENANCE_TEMPLATE,
                "Шаблон обслуживания удален",
                saved,
                null);

    }

    @Transactional
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

    @Transactional
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
