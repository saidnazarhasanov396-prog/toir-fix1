package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Notification;
import com.toir.enums.NotificationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    long countByRecipientIdAndStatus(UUID recipientId, NotificationStatus status);
}
