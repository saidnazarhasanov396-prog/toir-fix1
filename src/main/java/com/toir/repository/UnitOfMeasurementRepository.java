package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.UnitOfMeasurement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface UnitOfMeasurementRepository extends JpaRepository<UnitOfMeasurement, UUID> {
    boolean existsByCode(String code);
}
