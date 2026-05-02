package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.LaborEntry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface LaborEntryRepository extends JpaRepository<LaborEntry, UUID> {
    java.util.Optional<LaborEntry> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<LaborEntry> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<LaborEntry> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM labor_entries WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<LaborEntry> findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(@Param("workOrderId") UUID workOrderId);
}
