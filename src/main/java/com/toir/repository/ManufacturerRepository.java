package com.toir.repository;
import com.toir.entity.Manufacturer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, UUID> {
    boolean existsByCode(String code);
}
