package com.toir.equipmentpassport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EquipmentPassportRepository extends JpaRepository<EquipmentPassport, UUID> {
    Optional<EquipmentPassport> findByEquipmentId(UUID equipmentId);
    boolean existsByPassportNumber(String passportNumber);
}
