package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.TechnicalDocument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface TechnicalDocumentRepository extends JpaRepository<TechnicalDocument, UUID> {
    List<TechnicalDocument> findAllByEquipmentId(UUID equipmentId);
}
