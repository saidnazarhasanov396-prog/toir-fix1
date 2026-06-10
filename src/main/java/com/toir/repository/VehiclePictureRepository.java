package com.toir.repository;

import com.toir.entity.equipment.VehiclePicture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VehiclePictureRepository extends JpaRepository<VehiclePicture, UUID> {

    @Query("""
            select vp
            from VehiclePicture vp
            join fetch vp.file f
            join vp.vehicleDetails details
            where details.equipmentId = :equipmentId
              and details.isDeleted = false
              and vp.deleted = false
              and f.deleted = false
            order by vp.uploadedAt desc
            """)
    List<VehiclePicture> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query("""
            select vp
            from VehiclePicture vp
            join fetch vp.file f
            join vp.vehicleDetails details
            where vp.id = :id
              and details.equipmentId = :equipmentId
              and details.isDeleted = false
              and vp.deleted = false
              and f.deleted = false
            """)
    Optional<VehiclePicture> findByIdAndEquipmentId(@Param("id") UUID id, @Param("equipmentId") UUID equipmentId);

    @Query("""
            select vp
            from VehiclePicture vp
            join fetch vp.file f
            join vp.vehicleDetails details
            join Equipment equipment on equipment.id = details.equipmentId
            where vp.id = :id
              and details.isDeleted = false
              and equipment.isDeleted = false
              and vp.deleted = false
              and f.deleted = false
            """)
    Optional<VehiclePicture> findByIdActive(@Param("id") UUID id);
}
