package com.toir.repository;

import com.toir.entity.warehouse.WarehouseWriteoffRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseWriteoffRequestRepository extends JpaRepository<WarehouseWriteoffRequest, UUID> {

    Optional<WarehouseWriteoffRequest> findByIdAndIsDeletedFalse(UUID id);

    long countByIsDeletedFalse();
}
