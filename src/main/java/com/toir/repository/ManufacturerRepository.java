package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Manufacturer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface ManufacturerRepository extends JpaRepository<Manufacturer, UUID> {
    @Query(value = "SELECT EXISTS(SELECT 1 FROM manufacturers WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);
}
