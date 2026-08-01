package com.toir.service;

import com.toir.dto.notificationdevice.NotificationDeviceRegisterRequest;
import com.toir.entity.UserFcmToken;
import com.toir.enums.FcmDevicePlatform;
import com.toir.repository.UserFcmTokenRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeviceServiceTest {

    @Mock
    UserFcmTokenRepository repository;

    @InjectMocks
    NotificationDeviceService service;

    @Test
    void registerReactivatesExistingTokenForCurrentUser() {
        UUID userId = UUID.randomUUID();
        UserFcmToken existing = new UserFcmToken();
        existing.setToken("token-1");
        existing.setActive(false);
        when(repository.findByTokenAndIsDeletedFalse("token-1")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.register(userId, new NotificationDeviceRegisterRequest(
                " token-1 ",
                FcmDevicePlatform.IOS,
                "client-1",
                "uz-UZ"
        ));

        assertThat(existing.getUserId()).isEqualTo(userId);
        assertThat(existing.getPlatform()).isEqualTo(FcmDevicePlatform.IOS);
        assertThat(existing.getDeviceId()).isEqualTo("client-1");
        assertThat(existing.getLanguageCode()).isEqualTo("uz");
        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getLastSeenAt()).isNotNull();
    }

    @Test
    void unregisterMarksCurrentUsersTokenInactive() {
        UUID userId = UUID.randomUUID();
        UserFcmToken existing = new UserFcmToken();
        existing.setUserId(userId);
        existing.setToken("token-1");
        existing.setActive(true);
        when(repository.findByUserIdAndTokenAndIsDeletedFalse(userId, "token-1")).thenReturn(Optional.of(existing));

        service.unregister(userId, " token-1 ");

        ArgumentCaptor<UserFcmToken> captor = ArgumentCaptor.forClass(UserFcmToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }
}
