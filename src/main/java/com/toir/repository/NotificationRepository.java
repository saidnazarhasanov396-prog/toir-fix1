package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Notification;
import com.toir.enums.NotificationStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query(value = "SELECT * FROM notifications WHERE recipient_id = :recipientId AND is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<Notification> findAllByRecipientIdOrderByCreatedAtDesc(@Param("recipientId") UUID recipientId);

    @Query(value = "SELECT COUNT(*) FROM notifications WHERE recipient_id = :recipientId AND status = :status AND is_deleted = false", nativeQuery = true)
    long countByRecipientIdAndStatus(@Param("recipientId") UUID recipientId, @Param("status") NotificationStatus status);
}
