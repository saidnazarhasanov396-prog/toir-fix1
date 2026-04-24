package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentPassport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface EquipmentPassportRepository extends JpaRepository<EquipmentPassport, UUID> {
    Optional<EquipmentPassport> findByEquipmentId(UUID equipmentId);
    boolean existsByPassportNumber(String passportNumber);
    java.util.List<EquipmentPassport> findAllByEquipmentIdIn(java.util.Collection<UUID> equipmentIds);
}
