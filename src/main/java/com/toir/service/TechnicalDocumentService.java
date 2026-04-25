package com.toir.service;
import com.toir.entity.TechnicalDocument;
import com.toir.repository.TechnicalDocumentRepository;

import com.toir.exception.RestException;
import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TechnicalDocumentService {

    private final TechnicalDocumentRepository repository;

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
        return TechnicalDocumentDto.from(repository.save(d));
    }

    public void delete(UUID id) {
        TechnicalDocument d = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Document not found: " + id));
        d.setDeleted(true);
        repository.save(d);
    }
}
