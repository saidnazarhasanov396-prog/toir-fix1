package com.toir.service;

import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import com.toir.entity.FileAsset;
import com.toir.entity.TechnicalDocument;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.TechnicalDocumentRepository;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TechnicalDocumentService {

    private final TechnicalDocumentRepository repository;
    private final FileAssetRepository fileAssetRepository;
    private final AuditBuilderService auditBuilderService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;

    @Transactional(readOnly = true)
    public List<TechnicalDocumentDto> findByEquipment(UUID equipmentId) {
        List<TechnicalDocument> documents = repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId);
        if (documents.isEmpty()) {
            return List.of();
        }

        Set<UUID> fileIds = documents.stream()
                .map(TechnicalDocument::getFileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, TechnicalDocumentDto.FileRef> fileRefById = buildFileRefs(fileIds);

        return documents.stream()
                .map(document -> TechnicalDocumentDto.from(document, fileRefById.get(document.getFileId())))
                .toList();
    }

    @Transactional
    public TechnicalDocumentDto create(UUID equipmentId, TechnicalDocumentDto r) {
        equipmentStatusLifecycleService.assertOperationallyAllowed(equipmentId, "attach technical document");
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

    private Map<UUID, TechnicalDocumentDto.FileRef> buildFileRefs(Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return fileAssetRepository.findAllByIdInAndIsDeletedFalse(fileIds).stream()
                .collect(Collectors.toMap(FileAsset::getId, this::toFileRef, (a, b) -> a));
    }

    private TechnicalDocumentDto.FileRef toFileRef(FileAsset fileAsset) {
        String downloadUrl = String.format("/api/v1/files/assets/%s/download", fileAsset.getId());
        return new TechnicalDocumentDto.FileRef(
                fileAsset.getId(),
                fileAsset.getFileName(),
                fileAsset.getOriginalName(),
                fileAsset.getMimeType(),
                fileAsset.getSizeBytes(),
                downloadUrl
        );
    }
}
