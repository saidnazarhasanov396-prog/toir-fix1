package com.toir.repository;

import com.toir.entity.LaborEntry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface LaborEntryRepository extends JpaRepository<LaborEntry, UUID> {
    @Query(value = "SELECT * FROM labor_entries WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<LaborEntry> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM labor_entries WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<LaborEntry> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM labor_entries WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<LaborEntry> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM labor_entries WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM labor_entries WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM labor_entries WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<LaborEntry> findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(@Param("workOrderId") UUID workOrderId);
}
