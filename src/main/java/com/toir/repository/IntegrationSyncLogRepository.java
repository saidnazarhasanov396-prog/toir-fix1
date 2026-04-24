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
    @Query(value = "SELECT * FROM integration_sync_logs WHERE endpoint_id = :endpointId AND is_deleted = false ORDER BY started_at DESC LIMIT 50", nativeQuery = true)
    List<IntegrationSyncLog> findTop50ByEndpointIdOrderByStartedAtDesc(@Param("endpointId") UUID endpointId);

    @Query(value = "SELECT * FROM integration_sync_logs WHERE is_deleted = false ORDER BY started_at DESC LIMIT 100", nativeQuery = true)
    List<IntegrationSyncLog> findTop100ByOrderByStartedAtDesc();
}
