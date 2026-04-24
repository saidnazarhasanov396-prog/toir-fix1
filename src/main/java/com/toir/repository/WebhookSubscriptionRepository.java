package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WebhookSubscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    @Query(value = "SELECT EXISTS(SELECT 1 FROM webhook_subscriptions WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM webhook_subscriptions WHERE is_active = true AND is_deleted = false", nativeQuery = true)
    List<WebhookSubscription> findAllByActiveTrue();
}
