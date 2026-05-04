package com.toir.repository.inspection;

import com.toir.entity.inspection.InspectionCheckpoint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface InspectionCheckpointRepository extends JpaRepository<InspectionCheckpoint, UUID> {
    @Query(value = "SELECT * FROM inspection_checkpoints WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<InspectionCheckpoint> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM inspection_checkpoints WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionCheckpoint> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM inspection_checkpoints WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<InspectionCheckpoint> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM inspection_checkpoints WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM inspection_checkpoints WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM inspection_checkpoints WHERE route_id = :routeId AND is_deleted = false ORDER BY order_index ASC", nativeQuery = true)
    List<InspectionCheckpoint> findAllByRouteIdAndIsDeletedFalseOrderByOrderIndexAsc(@Param("routeId") UUID routeId);
}
