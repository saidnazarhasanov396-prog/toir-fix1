package com.toir.repository.inspection;

import com.toir.entity.inspection.InspectionRound;
import com.toir.enums.InspectionRoundStatus;
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

    @Query("""
            select ir from InspectionRound ir
            where ir.isDeleted = false
              and (:routeId is null or ir.route.id = :routeId)
              and (:performedBy is null or ir.performedBy = :performedBy)
              and (:status is null or ir.status = :status)
            order by ir.updatedAt desc
            """)
    List<InspectionRound> findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(
            @Param("routeId") UUID routeId,
            @Param("performedBy") UUID performedBy,
            @Param("status") InspectionRoundStatus status
    );

    @Query(value = "SELECT * FROM inspection_rounds WHERE performed_by = :performedBy AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByPerformedByAndIsDeletedFalseOrderByStartedAtDesc(@Param("performedBy") UUID performedBy);

    @Query(value = "SELECT * FROM inspection_rounds WHERE started_at > :since AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRound> findAllByStartedAtAfterAndIsDeletedFalseOrderByStartedAtDesc(@Param("since") Instant since);
}
