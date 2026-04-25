package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Warehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    java.util.Optional<Warehouse> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Warehouse> findAllByIsDeletedFalse();

    java.util.List<Warehouse> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM warehouses WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
