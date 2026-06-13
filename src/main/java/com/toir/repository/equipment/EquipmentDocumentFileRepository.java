package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentDocumentFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentDocumentFileRepository extends JpaRepository<EquipmentDocumentFile, UUID> {

    @Query("""
            select edf
            from EquipmentDocumentFile edf
            join fetch edf.file f
            join fetch edf.document ed
            join ed.equipment equipment
            where ed.id = :documentId
              and f.id = :fileId
              and equipment.id = :equipmentId
              and equipment.isDeleted = false
              and f.deleted = false
            """)
    Optional<EquipmentDocumentFile> findActiveByDocumentIdAndFileIdAndEquipmentId(
            @Param("documentId") UUID documentId,
            @Param("fileId") UUID fileId,
            @Param("equipmentId") UUID equipmentId
    );
}
