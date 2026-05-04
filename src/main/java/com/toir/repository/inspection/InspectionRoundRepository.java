package com.toir.repository.inspection;

import com.toir.entity.inspection.InspectionRound;
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

    @Query(value = """
            SELECT ir.* FROM inspection_rounds ir 
            WHERE  is_deleted = false 
                  AND (CAST(:routeId as uuid) IS NULL OR ir.route_id = CAST(:routeId as uuid)) 
                  AND (CAST(:performedBy as uuid) IS NULL OR ir.performed_by = CAST(:performedBy as uuid)) 
                  AND (CAST(:status as text) IS NULL OR ir.status = CAST(:status as text))
            ORDER BY updated_at DESC""", nativeQuery = true)
    List<InspectionRound> findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(
            @Param("routeId") UUID routeId,
            @Param("performedBy") UUID performedBy,
            @Param("status") String status
    );

    @Query(value = "SELECT * FROM inspection_rounds WHERE performed_by = :performedBy AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByPerformedByAndIsDeletedFalseOrderByStartedAtDesc(@Param("performedBy") UUID performedBy);

    @Query(value = "SELECT * FROM inspection_rounds WHERE started_at > :since AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByStartedAtAfterAndIsDeletedFalseOrderByStartedAtDesc(@Param("since") Instant since);
}
