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
    @Query(value = "SELECT * FROM inspection_checkpoints WHERE route_id = :routeId AND is_deleted = false ORDER BY order_index ASC", nativeQuery = true)
    List<InspectionCheckpoint> findAllByRouteIdOrderByOrderIndexAsc(@Param("routeId") UUID routeId);
}
