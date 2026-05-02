package com.toir.repository;

import com.toir.entity.EquipmentPassport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface EquipmentPassportRepository extends JpaRepository<EquipmentPassport, UUID> {
    @Query(value = "SELECT * FROM equipment_passports WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentPassport> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM equipment_passports WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentPassport> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM equipment_passports WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<EquipmentPassport> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_passports WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM equipment_passports WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM equipment_passports WHERE equipment_id = :equipmentId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentPassport> findByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_passports WHERE passport_number = :passportNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByPassportNumberAndIsDeletedFalse(@Param("passportNumber") String passportNumber);

    @Query(value = "SELECT * FROM equipment_passports WHERE equipment_id IN (:equipmentIds) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentPassport> findAllByEquipmentIdInAndIsDeletedFalse(@Param("equipmentIds") Collection<UUID> equipmentIds);
}
