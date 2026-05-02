package com.toir.repository;

import com.toir.entity.WebhookSubscription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    @Query(value = "SELECT * FROM webhook_subscriptions WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WebhookSubscription> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM webhook_subscriptions WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WebhookSubscription> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM webhook_subscriptions WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<WebhookSubscription> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM webhook_subscriptions WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM webhook_subscriptions WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM webhook_subscriptions WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM webhook_subscriptions WHERE is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WebhookSubscription> findAllByActiveTrueAndIsDeletedFalse();

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM webhook_subscriptions
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    boolean existsByCode(String code);
}
