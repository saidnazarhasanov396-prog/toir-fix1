package com.toir.uom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UnitOfMeasurementRepository extends JpaRepository<UnitOfMeasurement, UUID> {
    boolean existsByCode(String code);
}
