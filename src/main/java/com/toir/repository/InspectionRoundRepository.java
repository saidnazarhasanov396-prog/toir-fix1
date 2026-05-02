package com.toir.repository;

import com.toir.entity.InspectionRound;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface InspectionRoundRepository extends JpaRepository<InspectionRound, UUID> {
    @Query(value = "SELECT * FROM inspection_rounds WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<InspectionRound> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM inspection_rounds WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM inspection_rounds WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<InspectionRound> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM inspection_rounds WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM inspection_rounds WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM inspection_rounds WHERE route_id = :routeId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(@Param("routeId") UUID routeId);

    @Query(value = "SELECT * FROM inspection_rounds WHERE performed_by = :performedBy AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByPerformedByAndIsDeletedFalseOrderByStartedAtDesc(@Param("performedBy") UUID performedBy);

    @Query(value = "SELECT * FROM inspection_rounds WHERE started_at > :since AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByStartedAtAfterAndIsDeletedFalseOrderByStartedAtDesc(@Param("since") Instant since);
}
