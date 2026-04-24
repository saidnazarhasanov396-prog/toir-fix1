package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WebhookSubscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    boolean existsByCode(String code);
    List<WebhookSubscription> findAllByActiveTrue();
}
