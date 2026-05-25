package com.toir.repository;

import com.toir.entity.equipment.VehicleDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, UUID> {

    @Query("""
            select vd
            from VehicleDocument vd
            join fetch vd.file f
            join vd.vehicleDetails details
            where details.equipmentId = :equipmentId
              and details.isDeleted = false
              and f.deleted = false
            order by vd.createdAt desc
            """)
    List<VehicleDocument> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query("""
            select vd
            from VehicleDocument vd
            join fetch vd.file f
            join vd.vehicleDetails details
            where vd.id = :id
              and details.equipmentId = :equipmentId
              and details.isDeleted = false
              and f.deleted = false
            """)
    Optional<VehicleDocument> findByIdAndEquipmentId(@Param("id") UUID id, @Param("equipmentId") UUID equipmentId);

}
