package com.toir.repository;

import com.toir.entity.warehouse.WarehouseWriteoffAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WarehouseWriteoffAllocationRepository extends JpaRepository<WarehouseWriteoffAllocation, UUID> {

    List<WarehouseWriteoffAllocation> findAllByWriteoffRequestIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID writeoffRequestId);

    boolean existsByWriteoffRequestIdAndIsDeletedFalse(UUID writeoffRequestId);
}
