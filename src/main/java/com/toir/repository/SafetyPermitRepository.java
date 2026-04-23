package com.toir.repository;
import com.toir.entity.SafetyPermit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SafetyPermitRepository extends JpaRepository<SafetyPermit, UUID> {
    Optional<SafetyPermit> findByWorkOrderId(UUID workOrderId);
    boolean existsByPermitNumber(String permitNumber);
}
