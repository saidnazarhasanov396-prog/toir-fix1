package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentDocumentRepository extends JpaRepository<EquipmentDocument, UUID> {

    @Query("""
            select distinct ed
            from EquipmentDocument ed
            join fetch ed.file f
            left join fetch ed.files edf
            left join fetch edf.file edff
            join ed.equipment equipment
            where equipment.id = :equipmentId
              and equipment.isDeleted = false
              and f.deleted = false
            order by ed.createdAt desc, edf.sortOrder asc
            """)
    List<EquipmentDocument> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query("""
            select distinct ed
            from EquipmentDocument ed
            join fetch ed.file f
            left join fetch ed.files edf
            left join fetch edf.file edff
            join ed.equipment equipment
            where ed.id = :id
              and equipment.id = :equipmentId
              and equipment.isDeleted = false
              and f.deleted = false
            """)
    Optional<EquipmentDocument> findByIdAndEquipmentId(@Param("id") UUID id, @Param("equipmentId") UUID equipmentId);
}
