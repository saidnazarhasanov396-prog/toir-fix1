package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentPicture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentPictureRepository extends JpaRepository<EquipmentPicture, UUID> {

    @Query("""
            select ep
            from EquipmentPicture ep
            join fetch ep.file f
            join ep.equipment equipment
            where equipment.id = :equipmentId
              and equipment.isDeleted = false
              and ep.deleted = false
              and f.deleted = false
            order by ep.uploadedAt desc
            """)
    List<EquipmentPicture> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query("""
            select ep
            from EquipmentPicture ep
            join fetch ep.file f
            join ep.equipment equipment
            where ep.id = :id
              and equipment.id = :equipmentId
              and equipment.isDeleted = false
              and ep.deleted = false
              and f.deleted = false
            """)
    Optional<EquipmentPicture> findByIdAndEquipmentId(@Param("id") UUID id, @Param("equipmentId") UUID equipmentId);

    @Query("""
            select ep
            from EquipmentPicture ep
            join fetch ep.file f
            join ep.equipment equipment
            where ep.id = :id
              and equipment.isDeleted = false
              and ep.deleted = false
              and f.deleted = false
            """)
    Optional<EquipmentPicture> findByIdActive(@Param("id") UUID id);
}
