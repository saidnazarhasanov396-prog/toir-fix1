package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Warehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    @Query(value = "SELECT COUNT(*) > 0 FROM warehouses WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);
}
