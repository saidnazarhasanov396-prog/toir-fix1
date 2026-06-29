package com.toir.repository;

import com.toir.entity.warehouse.WarehouseTaskLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WarehouseTaskLineRepository extends JpaRepository<WarehouseTaskLine, UUID> {
}
