package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentPassport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface EquipmentPassportRepository extends JpaRepository<EquipmentPassport, UUID> {
    @Query(value = "SELECT * FROM equipment_passports WHERE equipment_id = :equipmentId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentPassport> findByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_passports WHERE passport_number = :passportNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByPassportNumber(@Param("passportNumber") String passportNumber);

    @Query(value = "SELECT * FROM equipment_passports WHERE equipment_id IN (:equipmentIds) AND is_deleted = false", nativeQuery = true)
    java.util.List<EquipmentPassport> findAllByEquipmentIdIn(@Param("equipmentIds") Collection<UUID> equipmentIds);
}
