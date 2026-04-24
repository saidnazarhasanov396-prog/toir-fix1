package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Manufacturer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface ManufacturerRepository extends JpaRepository<Manufacturer, UUID> {
    boolean existsByCode(String code);
}
