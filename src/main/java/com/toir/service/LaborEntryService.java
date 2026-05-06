package com.toir.service;

import com.toir.dto.laborentry.LaborEntryDto;
import com.toir.entity.LaborEntry;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.LaborEntryRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LaborEntryService {

    private final LaborEntryRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<LaborEntryDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId).stream()
                .map(LaborEntryDto::from).toList();
    }

    @Transactional
    public LaborEntryDto create(UUID workOrderId, LaborEntryDto r) {
        LaborEntry e = new LaborEntry();
        e.setWorkOrderId(workOrderId);
        apply(e, r);
        LaborEntry saved = repository.save(e);

        auditBuilderService.log(
                "labor_entry",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.LABOR_ENTRY,
                "Запись трудозатрат обновлена",
                e,
                saved
        );

        return LaborEntryDto.from(saved);
    }

    @Transactional
    public LaborEntryDto update(UUID id, LaborEntryDto r) {
        LaborEntry e = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Labor entry not found: " + id));
        apply(e, r);

        LaborEntry saved = repository.save(e);

        auditBuilderService.log(
                "labor_entry",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.LABOR_ENTRY,
                "Запись трудозатрат создана",
                null,
                saved
        );

        return LaborEntryDto.from(e);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = repository.findByIdAndIsDeletedFalse(id).orElseThrow();
        entity.setDeleted(true);
        LaborEntry saved = repository.save(entity);

        auditBuilderService.log(
                "labor_entry",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.LABOR_ENTRY,
                "Запись трудозатрат удалена",
                saved,
                null
        );

    }

    private void apply(LaborEntry e, LaborEntryDto r) {
        e.setUserId(r.userId());
        e.setContractorName(r.contractorName());
        e.setWorkDate(r.workDate());
        e.setHours(r.hours());
        e.setRate(r.rate());
        e.setDescription(r.description());
    }
}
