package com.toir.repository;

import com.toir.entity.IntegrationSyncLog;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface IntegrationSyncLogRepository extends JpaRepository<IntegrationSyncLog, UUID> {
    @Query(value = "SELECT * FROM integration_sync_logs WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<IntegrationSyncLog> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM integration_sync_logs WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<IntegrationSyncLog> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM integration_sync_logs WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<IntegrationSyncLog> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM integration_sync_logs WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM integration_sync_logs WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM integration_sync_logs WHERE endpoint_id = :endpointId AND is_deleted = false ORDER BY updated_at DESC LIMIT 50", nativeQuery = true)
    List<IntegrationSyncLog> findTop50ByEndpointIdAndIsDeletedFalseOrderByStartedAtDesc(@Param("endpointId") UUID endpointId);

    @Query(value = "SELECT * FROM integration_sync_logs WHERE is_deleted = false ORDER BY updated_at DESC LIMIT 100", nativeQuery = true)
    List<IntegrationSyncLog> findTop100ByIsDeletedFalseOrderByStartedAtDesc();
}
