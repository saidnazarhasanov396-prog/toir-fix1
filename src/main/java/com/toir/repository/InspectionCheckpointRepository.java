package com.toir.repository;
import com.toir.entity.InspectionCheckpoint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InspectionCheckpointRepository extends JpaRepository<InspectionCheckpoint, UUID> {
    List<InspectionCheckpoint> findAllByRouteIdOrderByOrderIndexAsc(UUID routeId);
}
