package com.toir.repository;

import com.toir.entity.warehouse.WarehouseBin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseBinRepository extends JpaRepository<WarehouseBin, UUID> {
    Optional<WarehouseBin> findByIdAndIsDeletedFalse(UUID id);
}
