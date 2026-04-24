package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.InspectionCheckpoint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface InspectionCheckpointRepository extends JpaRepository<InspectionCheckpoint, UUID> {
    List<InspectionCheckpoint> findAllByRouteIdOrderByOrderIndexAsc(UUID routeId);
}
