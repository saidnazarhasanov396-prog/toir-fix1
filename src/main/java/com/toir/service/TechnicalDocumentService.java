package com.toir.service;
import com.toir.entity.TechnicalDocument;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.TechnicalDocumentRepository;

import com.toir.exception.RestException;
import com.toir.dto.technicaldocument.TechnicalDocumentDto;
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
public class TechnicalDocumentService {

    private final TechnicalDocumentRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<TechnicalDocumentDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream().map(TechnicalDocumentDto::from).toList();
    }

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
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return TechnicalDocumentDto.from(saved);
    }

    public void delete(UUID id) {
        TechnicalDocument d = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Document not found: " + id));
        String oldJson = auditSerializationService.toJson(d);
        d.setDeleted(true);
        TechnicalDocument saved = repository.save(d);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private void audit(AuditAction action, UUID id, String oldJson, TechnicalDocument current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "technical_document",
                id != null ? id.toString() : null,
                action,
                AuditModule.TECHNICAL_DOCUMENT,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Технический документ создан";
            case UPDATE -> "Технический документ обновлен";
            case DELETE -> "Технический документ удален";
            default -> "Действие выполнено над техническим документом";
        };
    }
}
