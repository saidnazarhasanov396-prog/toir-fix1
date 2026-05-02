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
    java.util.Optional<InspectionRound> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<InspectionRound> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<InspectionRound> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM inspection_rounds WHERE route_id = :routeId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(@Param("routeId") UUID routeId);

    @Query(value = "SELECT * FROM inspection_rounds WHERE performed_by = :performedBy AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByPerformedByAndIsDeletedFalseOrderByStartedAtDesc(@Param("performedBy") UUID performedBy);

    @Query(value = "SELECT * FROM inspection_rounds WHERE started_at > :since AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByStartedAtAfterAndIsDeletedFalseOrderByStartedAtDesc(@Param("since") Instant since);
}
