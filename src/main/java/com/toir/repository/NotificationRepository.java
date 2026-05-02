package com.toir.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.toir.entity.Notification;
import com.toir.enums.NotificationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    java.util.Optional<Notification> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Notification> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<Notification> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM notifications WHERE recipient_id = :recipientId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Notification> findAllByRecipientIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("recipientId") UUID recipientId);

   @Query(value = "SELECT COUNT(*) FROM notifications WHERE recipient_id = :recipientId AND status = cast(:status as varchar) AND is_deleted = false", nativeQuery = true)
    long countByRecipientIdAndStatusAndIsDeletedFalse(@Param("recipientId") UUID recipientId, @Param("status") NotificationStatus status);
}
