package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.TechnicalDocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface TechnicalDocumentRepository extends JpaRepository<TechnicalDocument, UUID> {
    @Query(value = "SELECT * FROM technical_documents WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<TechnicalDocument> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);
}
