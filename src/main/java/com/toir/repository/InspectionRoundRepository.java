package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.InspectionRound;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Repository
public interface InspectionRoundRepository extends JpaRepository<InspectionRound, UUID> {
    @Query(value = "SELECT * FROM inspection_rounds WHERE route_id = :routeId AND is_deleted = false ORDER BY started_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByRouteIdOrderByStartedAtDesc(@Param("routeId") UUID routeId);

    @Query(value = "SELECT * FROM inspection_rounds WHERE performed_by = :performedBy AND is_deleted = false ORDER BY started_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByPerformedByOrderByStartedAtDesc(@Param("performedBy") UUID performedBy);

    @Query(value = "SELECT * FROM inspection_rounds WHERE started_at > :since AND is_deleted = false ORDER BY started_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByStartedAtAfterOrderByStartedAtDesc(@Param("since") Instant since);
}
