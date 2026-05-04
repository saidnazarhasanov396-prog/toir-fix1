package com.toir.service;
import com.toir.entity.MaintenanceOperation;
import com.toir.entity.MaintenanceTemplate;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.MaintenanceKind;
import com.toir.repository.MaintenanceOperationRepository;
import com.toir.repository.MaintenanceTemplateRepository;

import com.toir.exception.RestException;
import com.toir.dto.maintenancetemplate.MaintenanceOperationDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateRequest;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MaintenanceTemplateService {

    private final MaintenanceTemplateRepository repository;
    private final MaintenanceOperationRepository operationRepository;
    private final SparePartService sparePartService;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


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

    public MaintenanceTemplateDto create(MaintenanceTemplateRequest r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Template code already exists: " + r.code());
        }
        MaintenanceTemplate t = new MaintenanceTemplate();
        apply(t, r);
        MaintenanceTemplate saved = repository.save(t);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return MaintenanceTemplateDto.from(saved);
    }

    public MaintenanceTemplateDto update(UUID id, MaintenanceTemplateRequest r) {
        MaintenanceTemplate t = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(t);
        apply(t, r);
        audit(AuditAction.UPDATE, t.getId(), oldJson, t);
        return MaintenanceTemplateDto.from(t);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        MaintenanceTemplate saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

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

    private void audit(AuditAction action, UUID id, String oldJson, MaintenanceTemplate current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log("maintenance_template", id != null ? id.toString() : null, action,
                AuditModule.MAINTENANCE_TEMPLATE, auditMessage(action), oldJson, newJson);
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Шаблон обслуживания создан";
            case UPDATE -> "Шаблон обслуживания обновлен";
            case DELETE -> "Шаблон обслуживания удален";
            default -> "Действие выполнено над шаблоном обслуживания";
        };
    }
}
