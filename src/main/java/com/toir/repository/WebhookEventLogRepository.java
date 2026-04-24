package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WebhookEventLog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, UUID> {
    List<WebhookEventLog> findTop50BySubscriptionIdOrderByFiredAtDesc(UUID subscriptionId);
    List<WebhookEventLog> findTop50ByEventCodeOrderByFiredAtDesc(String eventCode);
}
