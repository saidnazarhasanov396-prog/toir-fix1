package com.toir.repository;

import com.toir.entity.IntegrationSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IntegrationSyncLogRepository extends JpaRepository<IntegrationSyncLog, UUID> {
    List<IntegrationSyncLog> findTop50ByEndpointIdOrderByStartedAtDesc(UUID endpointId);
    List<IntegrationSyncLog> findTop100ByOrderByStartedAtDesc();
}
