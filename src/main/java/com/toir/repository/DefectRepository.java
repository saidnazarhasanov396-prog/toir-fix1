package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Defect;
import com.toir.enums.DefectStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface DefectRepository extends JpaRepository<Defect, UUID> {
    @Query(value = "SELECT COUNT(*) > 0 FROM defects WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM defects WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<Defect> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT COUNT(*) FROM defects WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatus(@Param("status") DefectStatus status);
}
