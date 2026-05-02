package com.toir.repository;

import com.toir.entity.WebhookEventLog;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, UUID> {
    @Query(value = "SELECT * FROM webhook_event_log WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WebhookEventLog> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM webhook_event_log WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WebhookEventLog> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM webhook_event_log WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<WebhookEventLog> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM webhook_event_log WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM webhook_event_log WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM webhook_event_log WHERE subscription_id = :subscriptionId AND is_deleted = false ORDER BY updated_at DESC LIMIT 50", nativeQuery = true)
    List<WebhookEventLog> findTop50BySubscriptionIdAndIsDeletedFalseOrderByFiredAtDesc(@Param("subscriptionId") UUID subscriptionId);

    @Query(value = "SELECT * FROM webhook_event_log WHERE event_code = :eventCode AND is_deleted = false ORDER BY updated_at DESC LIMIT 50", nativeQuery = true)
    List<WebhookEventLog> findTop50ByEventCodeAndIsDeletedFalseOrderByFiredAtDesc(@Param("eventCode") String eventCode);
}
