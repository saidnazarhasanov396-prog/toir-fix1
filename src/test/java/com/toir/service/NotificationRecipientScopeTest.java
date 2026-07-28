package com.toir.service;

import com.toir.entity.Notification;
import com.toir.repository.NotificationRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRecipientScopeTest {

    @Mock NotificationRepository repository;
    @Mock UserRepository userRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock ScopeAccessService scopeAccessService;
    @Mock FirebasePushNotificationSender firebasePushNotificationSender;
    @Mock NotificationNavigationBuilder navigationBuilder;
    @InjectMocks NotificationService service;

    @Test
    void ownerCanReadNotificationById() {
        UUID ownerId = UUID.randomUUID();
        Notification notification = notification(ownerId);
        when(repository.findByIdAndIsDeletedFalse(notification.getId()))
                .thenReturn(Optional.of(notification));

        assertThat(service.findById(notification.getId(), ownerId, false).id())
                .isEqualTo(notification.getId());
    }

    @Test
    void otherRecipientIsDenied() {
        Notification notification = notification(UUID.randomUUID());
        when(repository.findByIdAndIsDeletedFalse(notification.getId()))
                .thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.findById(
                notification.getId(),
                UUID.randomUUID(),
                false
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void notificationAdminCanReadAnotherRecipientsNotification() {
        Notification notification = notification(UUID.randomUUID());
        when(repository.findByIdAndIsDeletedFalse(notification.getId()))
                .thenReturn(Optional.of(notification));

        assertThat(service.findById(notification.getId(), UUID.randomUUID(), true).id())
                .isEqualTo(notification.getId());
    }

    private Notification notification(UUID ownerId) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientId(ownerId);
        notification.setTitle("Title");
        notification.setMessage("Message");
        return notification;
    }
}
