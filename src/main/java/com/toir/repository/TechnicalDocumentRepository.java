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
    java.util.Optional<TechnicalDocument> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<TechnicalDocument> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<TechnicalDocument> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM technical_documents WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TechnicalDocument> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);
}
