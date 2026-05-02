package com.toir.repository;

import org.springframework.stereotype.Repository;

import com.toir.entity.IntegrationSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface IntegrationSyncLogRepository extends JpaRepository<IntegrationSyncLog, UUID> {
    java.util.Optional<IntegrationSyncLog> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<IntegrationSyncLog> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<IntegrationSyncLog> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM integration_sync_logs WHERE endpoint_id = :endpointId AND is_deleted = false ORDER BY updated_at DESC LIMIT 50", nativeQuery = true)
    List<IntegrationSyncLog> findTop50ByEndpointIdAndIsDeletedFalseOrderByStartedAtDesc(@Param("endpointId") UUID endpointId);

    @Query(value = "SELECT * FROM integration_sync_logs WHERE is_deleted = false ORDER BY updated_at DESC LIMIT 100", nativeQuery = true)
    List<IntegrationSyncLog> findTop100ByIsDeletedFalseOrderByStartedAtDesc();
}
