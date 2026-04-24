package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.SafetyPermit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface SafetyPermitRepository extends JpaRepository<SafetyPermit, UUID> {
    Optional<SafetyPermit> findByWorkOrderId(UUID workOrderId);
    boolean existsByPermitNumber(String permitNumber);
}
