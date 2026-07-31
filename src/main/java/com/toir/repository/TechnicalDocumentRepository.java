package com.toir.repository;

import com.toir.entity.TechnicalDocument;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface TechnicalDocumentRepository extends JpaRepository<TechnicalDocument, UUID> {
    @Query(value = "SELECT * FROM technical_documents WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<TechnicalDocument> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM technical_documents WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TechnicalDocument> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM technical_documents WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<TechnicalDocument> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM technical_documents WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM technical_documents WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM technical_documents WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TechnicalDocument> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM technical_documents WHERE equipment_node_id = cast(:equipmentNodeId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<TechnicalDocument> findAllByEquipmentNodeIdAndIsDeletedFalse(@Param("equipmentNodeId") UUID equipmentNodeId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM technical_documents WHERE equipment_node_id = cast(:equipmentNodeId as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByEquipmentNodeIdAndIsDeletedFalse(@Param("equipmentNodeId") UUID equipmentNodeId);

    @Query(value = """
            SELECT *
            FROM technical_documents
            WHERE equipment_id = :equipmentId
              AND updated_at <= :asOf
              AND is_deleted = false
            ORDER BY document_date ASC NULLS LAST, id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<TechnicalDocument> findLifecycleDocuments(
            @Param("equipmentId") UUID equipmentId,
            @Param("asOf") java.time.Instant asOf,
            @Param("limitPlusOne") int limitPlusOne);
}
