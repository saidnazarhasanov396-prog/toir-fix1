package com.toir.service.maintanance;

import com.toir.dto.maintenancetemplate.MaintenanceTemplateSparePartRequirementDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateSparePartRequirementRequest;
import com.toir.entity.SparePart;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.entity.maintenance.MaintenanceTemplateSparePartRequirement;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.repository.maintenance.MaintenanceTemplateSparePartRequirementRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MaintenanceTemplateSparePartRequirementService {

    private final MaintenanceTemplateSparePartRequirementRepository repository;
    private final MaintenanceTemplateRepository templateRepository;
    private final MaintenanceOperationRepository operationRepository;
    private final SparePartRepository sparePartRepository;

    @Transactional(readOnly = true)
    public List<MaintenanceTemplateSparePartRequirementDto> findByTemplate(UUID templateId) {
        requireTemplate(templateId);
        return repository.findActiveByTemplateId(templateId).stream()
                .map(MaintenanceTemplateSparePartRequirementDto::from)
                .toList();
    }

    @Transactional
    public MaintenanceTemplateSparePartRequirementDto create(UUID templateId,
                                                            MaintenanceTemplateSparePartRequirementRequest request) {
        validateQuantity(request.quantity());
        MaintenanceTemplate template = requireTemplate(templateId);
        SparePart sparePart = requireSparePart(request.sparePartId());
        MaintenanceOperation operation = requireOperationForTemplate(templateId, request.operationId());
        assertNoDuplicate(templateId, request.operationId(), request.sparePartId(), null);

        MaintenanceTemplateSparePartRequirement entity = new MaintenanceTemplateSparePartRequirement();
        entity.setTemplate(template);
        entity.setOperation(operation);
        entity.setSparePart(sparePart);
        apply(entity, request, sparePart);
        return MaintenanceTemplateSparePartRequirementDto.from(repository.save(entity));
    }

    @Transactional
    public MaintenanceTemplateSparePartRequirementDto update(UUID templateId,
                                                            UUID id,
                                                            MaintenanceTemplateSparePartRequirementRequest request) {
        validateQuantity(request.quantity());
        requireTemplate(templateId);
        MaintenanceTemplateSparePartRequirement entity = repository.findByIdAndTemplateIdAndIsDeletedFalse(id, templateId)
                .orElseThrow(() -> RestException.notFound("Maintenance template spare part requirement not found: " + id));
        SparePart sparePart = requireSparePart(request.sparePartId());
        MaintenanceOperation operation = requireOperationForTemplate(templateId, request.operationId());
        assertNoDuplicate(templateId, request.operationId(), request.sparePartId(), id);

        entity.setOperation(operation);
        entity.setSparePart(sparePart);
        apply(entity, request, sparePart);
        return MaintenanceTemplateSparePartRequirementDto.from(repository.save(entity));
    }

    @Transactional
    public void delete(UUID templateId, UUID id) {
        MaintenanceTemplateSparePartRequirement entity = repository.findByIdAndTemplateIdAndIsDeletedFalse(id, templateId)
                .orElseThrow(() -> RestException.notFound("Maintenance template spare part requirement not found: " + id));
        entity.setActive(false);
        repository.save(entity);
    }

    private MaintenanceTemplate requireTemplate(UUID templateId) {
        return templateRepository.findByIdAndIsDeletedFalse(templateId)
                .orElseThrow(() -> RestException.notFound("Maintenance template not found: " + templateId));
    }

    private SparePart requireSparePart(UUID sparePartId) {
        if (sparePartId == null) {
            throw RestException.badRequest("sparePartId is required");
        }
        return sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + sparePartId));
    }

    private MaintenanceOperation requireOperationForTemplate(UUID templateId, UUID operationId) {
        if (operationId == null) {
            return null;
        }
        MaintenanceOperation operation = operationRepository.findByIdAndIsDeletedFalse(operationId)
                .orElseThrow(() -> RestException.notFound("Maintenance operation not found: " + operationId));
        if (operation.getTemplate() == null || !templateId.equals(operation.getTemplate().getId())) {
            throw RestException.badRequest("operation belongs to another template");
        }
        return operation;
    }

    private void validateQuantity(double quantity) {
        if (quantity <= 0) {
            throw RestException.badRequest("quantity must be positive");
        }
    }

    private void assertNoDuplicate(UUID templateId, UUID operationId, UUID sparePartId, UUID excludeId) {
        if (repository.existsActiveByTemplateOperationAndSparePart(templateId, operationId, sparePartId, excludeId)) {
            throw RestException.badRequest("Maintenance template spare part requirement already exists");
        }
    }

    private void apply(MaintenanceTemplateSparePartRequirement entity,
                       MaintenanceTemplateSparePartRequirementRequest request,
                       SparePart sparePart) {
        entity.setQuantity(request.quantity());
        String sparePartUnit = sparePart.getUnit();
        String requestedUnit = StringUtils.hasText(request.unit()) ? request.unit().trim() : null;
        if (requestedUnit != null
                && StringUtils.hasText(sparePartUnit)
                && !requestedUnit.equalsIgnoreCase(sparePartUnit.trim())) {
            throw RestException.badRequest("unit must match spare part unit");
        }
        entity.setUnit(requestedUnit == null ? sparePartUnit : requestedUnit);
        entity.setCriticality(StringUtils.hasText(request.criticality()) ? request.criticality().trim() : null);
        entity.setNotes(StringUtils.hasText(request.notes()) ? request.notes().trim() : null);
        entity.setActive(request.active() == null || request.active());
    }
}
