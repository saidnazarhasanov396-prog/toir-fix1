package com.toir.service;
import com.toir.entity.Contractor;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.ContractorRepository;

import com.toir.exception.RestException;
import com.toir.dto.contractor.ContractorDto;
import com.toir.dto.contractor.ContractorRequest;
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
public class ContractorService {

    private final ContractorRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<ContractorDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(ContractorDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ContractorDto findById(UUID id) {
        return ContractorDto.from(getOrThrow(id));
    }

    public ContractorDto create(ContractorRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Contractor code already exists: " + request.code());
        }
        Contractor entity = new Contractor();
        apply(entity, request);
        Contractor saved = repository.save(entity);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return ContractorDto.from(saved);
    }

    public ContractorDto update(UUID id, ContractorRequest request) {
        Contractor entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        apply(entity, request);
        audit(AuditAction.UPDATE, entity.getId(), oldJson, entity);
        return ContractorDto.from(entity);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        Contractor saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private Contractor getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Contractor not found: " + id));
    }

    private void apply(Contractor entity, ContractorRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setTaxNumber(request.taxNumber());
        entity.setContactPerson(request.contactPerson());
        entity.setPhone(request.phone());
        entity.setEmail(request.email());
        entity.setSpecialization(request.specialization());
        if (request.status() != null) entity.setStatus(request.status());
    }

    private void audit(AuditAction action, UUID id, String oldJson, Contractor current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "contractor",
                id != null ? id.toString() : null,
                action,
                AuditModule.CONTRACTOR,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Подрядчик создан";
            case UPDATE -> "Подрядчик обновлен";
            case DELETE -> "Подрядчик удален";
            default -> "Действие выполнено над подрядчиком";
        };
    }
}
