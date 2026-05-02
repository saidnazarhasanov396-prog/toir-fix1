package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.InspectionCheckpoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface InspectionCheckpointRepository extends JpaRepository<InspectionCheckpoint, UUID> {
    java.util.Optional<InspectionCheckpoint> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<InspectionCheckpoint> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<InspectionCheckpoint> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM inspection_checkpoints WHERE route_id = :routeId AND is_deleted = false ORDER BY order_index ASC", nativeQuery = true)
    List<InspectionCheckpoint> findAllByRouteIdAndIsDeletedFalseOrderByOrderIndexAsc(@Param("routeId") UUID routeId);
}
