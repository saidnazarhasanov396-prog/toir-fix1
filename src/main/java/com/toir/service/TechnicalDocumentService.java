package com.toir.service;
import com.toir.entity.TechnicalDocument;
import com.toir.repository.TechnicalDocumentRepository;

import com.toir.exception.RestException;
import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TechnicalDocumentService {

    private final TechnicalDocumentRepository repository;

    public TechnicalDocumentService(TechnicalDocumentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TechnicalDocumentDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentId(equipmentId).stream().map(TechnicalDocumentDto::from).toList();
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
        TechnicalDocument d = repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Document not found: " + id));
        repository.delete(d);
    }
}
