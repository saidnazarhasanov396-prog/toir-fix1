package com.toir.service;

import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import com.toir.entity.TechnicalDocument;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.TechnicalDocumentRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TechnicalDocumentService {

    private final TechnicalDocumentRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<TechnicalDocumentDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream().map(TechnicalDocumentDto::from).toList();
    }

    @Transactional
    public TechnicalDocumentDto create(UUID equipmentId, TechnicalDocumentDto r) {
        TechnicalDocument d = new TechnicalDocument();
        d.setEquipmentId(equipmentId);
        d.setFileId(r.fileId());
        d.setTitle(r.title());
        d.setRevision(r.revision());
        d.setType(r.type());
        d.setDocumentDate(r.documentDate());
        d.setUploadedById(r.uploadedById());
        TechnicalDocument saved = repository.save(d);

        auditBuilderService.log(
                "technical_document",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.TECHNICAL_DOCUMENT,
                "Технический документ создан",
                null,
                saved
        );

        return TechnicalDocumentDto.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        TechnicalDocument d = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Document not found: " + id));
        d.setDeleted(true);
        TechnicalDocument saved = repository.save(d);

        auditBuilderService.log(
                "technical_document",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.TECHNICAL_DOCUMENT,
                "Технический документ удален",
                saved,
                null
        );

    }
}
