package com.toir.service;
import com.toir.entity.Notification;
import com.toir.enums.NotificationStatus;
import com.toir.repository.NotificationRepository;

import com.toir.exception.RestException;
import com.toir.dto.notification.NotificationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;


    @Transactional(readOnly = true)
    public List<NotificationDto> findForUser(UUID recipientId) {
        return com.toir.util.UpdatedAtSorter.descending(repository.findAllByRecipientIdAndIsDeletedFalseOrderByCreatedAtDesc(recipientId)).stream()
                .map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID recipientId) {
        return repository.countByRecipientIdAndStatusAndIsDeletedFalse(recipientId, NotificationStatus.SENT);
    }

    public NotificationDto send(NotificationDto r) {
        Notification n = new Notification();
        n.setRecipientId(r.recipientId());
        n.setTitle(r.title());
        n.setMessage(r.message());
        if (r.channel() != null) n.setChannel(r.channel());
        if (r.severity() != null) n.setSeverity(r.severity());
        n.setEntityType(r.entityType());
        n.setEntityId(r.entityId());
        n.setStatus(NotificationStatus.SENT);
        return NotificationDto.from(repository.save(n));
    }

    public NotificationDto markRead(UUID id) {
        Notification n = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Notification not found: " + id));
        n.setStatus(NotificationStatus.READ);
        n.setReadAt(Instant.now());
        return NotificationDto.from(n);
    }
}
