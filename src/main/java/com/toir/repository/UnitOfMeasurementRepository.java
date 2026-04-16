package com.toir.repository;
import com.toir.entity.UnitOfMeasurement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UnitOfMeasurementRepository extends JpaRepository<UnitOfMeasurement, UUID> {
    boolean existsByCode(String code);
}
