package com.toir.service;
import com.toir.entity.LaborEntry;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.LaborEntryRepository;

import com.toir.exception.RestException;
import com.toir.dto.laborentry.LaborEntryDto;
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
public class LaborEntryService {

    private final LaborEntryRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<LaborEntryDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId).stream()
                .map(LaborEntryDto::from).toList();
    }

    public LaborEntryDto create(UUID workOrderId, LaborEntryDto r) {
        LaborEntry e = new LaborEntry();
        e.setWorkOrderId(workOrderId);
        apply(e, r);
        LaborEntry saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return LaborEntryDto.from(saved);
    }

    public LaborEntryDto update(UUID id, LaborEntryDto r) {
        LaborEntry e = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Labor entry not found: " + id));
        String oldJson = auditSerializationService.toJson(e);
        apply(e, r);
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return LaborEntryDto.from(e);
    }

    public void delete(UUID id) {
        var entity = repository.findByIdAndIsDeletedFalse(id).orElseThrow();
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        LaborEntry saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private void apply(LaborEntry e, LaborEntryDto r) {
        e.setUserId(r.userId());
        e.setContractorName(r.contractorName());
        e.setWorkDate(r.workDate());
        e.setHours(r.hours());
        e.setRate(r.rate());
        e.setDescription(r.description());
    }

    private void audit(AuditAction action, UUID id, String oldJson, LaborEntry current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "labor_entry",
                id != null ? id.toString() : null,
                action,
                AuditModule.LABOR_ENTRY,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Запись трудозатрат создана";
            case UPDATE -> "Запись трудозатрат обновлена";
            case DELETE -> "Запись трудозатрат удалена";
            default -> "Действие выполнено над записью трудозатрат";
        };
    }
}
