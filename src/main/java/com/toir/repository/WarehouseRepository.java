package com.toir.repository;
import com.toir.entity.Warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    boolean existsByCode(String code);
}
