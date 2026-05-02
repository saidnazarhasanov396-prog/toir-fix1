package com.toir.repository;

import com.toir.entity.ActualCost;
import com.toir.enums.ActualCostStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ActualCostRepository extends JpaRepository<ActualCost, UUID> {
    @Query(value = "SELECT * FROM actual_costs WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ActualCost> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM actual_costs WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ActualCost> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM actual_costs WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ActualCost> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM actual_costs WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM actual_costs WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM actual_costs WHERE work_order_id = cast(:workOrderId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ActualCost> findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("workOrderId") UUID workOrderId);

    @Query(value = "SELECT * FROM actual_costs WHERE status = cast(:status as varchar) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ActualCost> findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("status") ActualCostStatus status);
}
