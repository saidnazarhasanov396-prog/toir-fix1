package com.toir.technicaldocument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TechnicalDocumentRepository extends JpaRepository<TechnicalDocument, UUID> {
    List<TechnicalDocument> findAllByEquipmentId(UUID equipmentId);
}
