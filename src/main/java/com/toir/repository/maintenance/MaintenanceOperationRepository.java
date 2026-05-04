package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceOperation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface MaintenanceOperationRepository extends JpaRepository<MaintenanceOperation, UUID> {
    @Query(value = "SELECT * FROM maintenance_operations WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<MaintenanceOperation> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM maintenance_operations WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceOperation> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM maintenance_operations WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<MaintenanceOperation> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM maintenance_operations WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM maintenance_operations WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

}
