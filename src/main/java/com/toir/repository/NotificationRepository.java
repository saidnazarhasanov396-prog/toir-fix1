package com.toir.repository;

import com.toir.entity.Notification;
import com.toir.enums.NotificationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query(value = "SELECT * FROM notifications WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Notification> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM notifications WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Notification> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM notifications WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Notification> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM notifications WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM notifications WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM notifications WHERE recipient_id = :recipientId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Notification> findAllByRecipientIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("recipientId") UUID recipientId);

    @Query(value = """
            SELECT *
            FROM notifications
            WHERE is_deleted = false
              AND entity_type IS NOT NULL
              AND UPPER(entity_type) LIKE '%COST%'
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Notification> findAllFinancialReviewInboxOrderByCreatedAtDesc();

   @Query(value = "SELECT COUNT(*) FROM notifications WHERE recipient_id = :recipientId AND status = cast(:status as varchar) AND is_deleted = false", nativeQuery = true)
    long countByRecipientIdAndStatusAndIsDeletedFalse(@Param("recipientId") UUID recipientId, @Param("status") NotificationStatus status);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM notifications
                WHERE recipient_id = cast(:recipientId as uuid)
                  AND entity_type = :entityType
                  AND entity_id = :entityId
                  AND title = :title
                  AND status IN ('PENDING', 'SENT')
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsOpenForRecipientAndEntity(@Param("recipientId") UUID recipientId,
                                            @Param("entityType") String entityType,
                                            @Param("entityId") String entityId,
                                            @Param("title") String title);
}
