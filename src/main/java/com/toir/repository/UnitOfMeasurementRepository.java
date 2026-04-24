package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.UnitOfMeasurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface UnitOfMeasurementRepository extends JpaRepository<UnitOfMeasurement, UUID> {
    @Query(value = "SELECT EXISTS(SELECT 1 FROM units_of_measurement WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);
}
