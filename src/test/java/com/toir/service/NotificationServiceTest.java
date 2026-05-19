package com.toir.service;

import com.toir.dto.notification.NotificationDto;
import com.toir.entity.Notification;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository repository;

    @InjectMocks
    NotificationService service;

    @Test
    void findForUserFallsBackToInfoWhenSeverityIsNull() {
        UUID recipientId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientId(recipientId);
        notification.setTitle("Title");
        notification.setMessage("Message");
        notification.setSeverity(null);
        notification.setStatus(NotificationStatus.SENT);

        when(repository.findAllByRecipientIdAndIsDeletedFalseOrderByCreatedAtDesc(recipientId))
                .thenReturn(List.of(notification));

        List<NotificationDto> result = service.findForUser(recipientId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().severity()).isEqualTo(NotificationSeverity.INFO);
    }
}
