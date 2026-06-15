package com.toir.service;

import com.toir.dto.notificationdevice.NotificationDeviceDto;
import com.toir.dto.notificationdevice.NotificationDeviceRegisterRequest;
import com.toir.entity.UserFcmToken;
import com.toir.exception.RestException;
import com.toir.repository.UserFcmTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class NotificationDeviceService {

    private final UserFcmTokenRepository repository;

    @Transactional
    public NotificationDeviceDto register(UUID userId, NotificationDeviceRegisterRequest request) {
        if (userId == null) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        String normalizedToken = normalizeToken(request.token());
        Instant now = Instant.now();
        UserFcmToken entity = repository.findByTokenAndIsDeletedFalse(normalizedToken)
                .orElseGet(UserFcmToken::new);
        entity.setUserId(userId);
        entity.setToken(normalizedToken);
        entity.setPlatform(request.platform());
        entity.setDeviceId(normalizeOptional(request.deviceId()));
        entity.setActive(true);
        entity.setLastSeenAt(now);
        return NotificationDeviceDto.from(repository.save(entity));
    }

    @Transactional
    public void unregister(UUID userId, String token) {
        if (userId == null) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        String normalizedToken = normalizeToken(token);
        UserFcmToken entity = repository.findByUserIdAndTokenAndIsDeletedFalse(userId, normalizedToken)
                .orElseThrow(() -> RestException.notFound("Notification device token not found"));
        entity.setActive(false);
        entity.setLastSeenAt(Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<NotificationDeviceDto> findMine(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return repository.findAllByUserIdAndIsDeletedFalseOrderByLastSeenAtDesc(userId).stream()
                .map(NotificationDeviceDto::from)
                .toList();
    }

    private String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw RestException.badRequest("FCM token is required");
        }
        return token.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
