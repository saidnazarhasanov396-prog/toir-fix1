package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WebhookEventLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, UUID> {
    java.util.Optional<WebhookEventLog> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<WebhookEventLog> findAllByIsDeletedFalse();

    java.util.List<WebhookEventLog> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM webhook_event_log WHERE subscription_id = :subscriptionId AND is_deleted = false ORDER BY fired_at DESC LIMIT 50", nativeQuery = true)
    List<WebhookEventLog> findTop50BySubscriptionIdAndIsDeletedFalseOrderByFiredAtDesc(@Param("subscriptionId") UUID subscriptionId);

    @Query(value = "SELECT * FROM webhook_event_log WHERE event_code = :eventCode AND is_deleted = false ORDER BY fired_at DESC LIMIT 50", nativeQuery = true)
    List<WebhookEventLog> findTop50ByEventCodeAndIsDeletedFalseOrderByFiredAtDesc(@Param("eventCode") String eventCode);
}
