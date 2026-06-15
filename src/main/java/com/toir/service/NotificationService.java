package com.toir.service;
import com.toir.entity.Notification;
import com.toir.entity.users.Employee;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.enums.UserStatus;
import com.toir.repository.NotificationRepository;

import com.toir.exception.RestException;
import com.toir.dto.notification.NotificationDto;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final ScopeAccessService scopeAccessService;
    private final FirebasePushNotificationSender firebasePushNotificationSender;


    @Transactional(readOnly = true)
    public List<NotificationDto> findForUser(UUID recipientId) {
        if (recipientId == null) {
            return List.of();
        }
        return repository.findAllByRecipientIdAndIsDeletedFalseOrderByCreatedAtDesc(recipientId).stream()
                .map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID recipientId) {
        if (recipientId == null) {
            return 0;
        }
        return repository.countByRecipientIdAndStatusAndIsDeletedFalse(recipientId, NotificationStatus.SENT);
    }

    @Transactional
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
        NotificationDto saved = NotificationDto.from(repository.save(n));
        trySendPush(saved);
        return saved;
    }

    @Transactional
    public NotificationDto markRead(UUID id) {
        return markRead(id, scopeAccessService.currentUserIdOrNull(), scopeAccessService.isScopeAdmin());
    }

    @Transactional
    public NotificationDto markRead(UUID id, UUID currentUserId, boolean scopeAdmin) {
        Notification n = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Notification not found: " + id));
        if (!scopeAdmin && (currentUserId == null || !currentUserId.equals(n.getRecipientId()))) {
            throw new AccessDeniedException("Access denied by notification recipient scope");
        }
        n.setStatus(NotificationStatus.READ);
        n.setReadAt(Instant.now());
        return NotificationDto.from(n);
    }

    @Transactional
    public Optional<NotificationDto> notifyUser(UUID recipientId,
                                                String title,
                                                String message,
                                                NotificationSeverity severity,
                                                String entityType,
                                                String entityId) {
        if (recipientId == null || !StringUtils.hasText(title) || !StringUtils.hasText(message)) {
            return Optional.empty();
        }
        if (isDuplicateOpen(recipientId, title, entityType, entityId)) {
            return Optional.empty();
        }
        return Optional.of(send(new NotificationDto(
                null,
                recipientId,
                title,
                message,
                NotificationChannel.WEB,
                NotificationStatus.SENT,
                severity != null ? severity : NotificationSeverity.INFO,
                entityType,
                entityId,
                null
        )));
    }

    @Transactional
    public Optional<NotificationDto> notifyEmployee(UUID employeeId,
                                                    String title,
                                                    String message,
                                                    NotificationSeverity severity,
                                                    String entityType,
                                                    String entityId) {
        if (employeeId == null) {
            return Optional.empty();
        }
        return employeeRepository.findByIdAndIsDeletedFalse(employeeId)
                .map(Employee::getUserId)
                .flatMap(userId -> notifyUser(userId, title, message, severity, entityType, entityId));
    }

    @Transactional
    public List<NotificationDto> notifyDepartmentByPermission(UUID departmentId,
                                                              String permission,
                                                              String title,
                                                              String message,
                                                              NotificationSeverity severity,
                                                              String entityType,
                                                              String entityId) {
        if (departmentId == null || !StringUtils.hasText(permission)) {
            return List.of();
        }
        List<User> candidates = userRepository.findAllWithRolesAndIsDeletedFalse().stream()
                .filter(this::isActive)
                .filter(user -> departmentId.equals(user.getDepartmentId()))
                .filter(user -> hasPermission(user, permission))
                .sorted(Comparator.comparing(User::getUsername, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<User> departmentScoped = candidates.stream()
                .filter(user -> !isAdminOrWildcard(user))
                .toList();
        List<User> recipients = departmentScoped.isEmpty() ? candidates : departmentScoped;

        return recipients.stream()
                .map(User::getId)
                .distinct()
                .map(userId -> notifyUser(userId, title, message, severity, entityType, entityId))
                .flatMap(Optional::stream)
                .toList();
    }

    private boolean isDuplicateOpen(UUID recipientId, String title, String entityType, String entityId) {
        if (!StringUtils.hasText(entityType) || !StringUtils.hasText(entityId)) {
            return false;
        }
        return repository.existsOpenForRecipientAndEntity(recipientId, entityType, entityId, title);
    }

    private boolean isActive(User user) {
        return user != null && (user.getStatus() == null || user.getStatus() == UserStatus.ACTIVE);
    }

    private boolean hasPermission(User user, String permission) {
        return roleStream(user)
                .flatMap(role -> {
                    Stream<String> permissions = role.getPermissions() == null
                            ? Stream.empty()
                            : role.getPermissions().stream();
                    return Stream.concat(Stream.of(role.getCode()), permissions);
                })
                .filter(Objects::nonNull)
                .anyMatch(value -> PermissionConstants.WILDCARD.equals(value) || permission.equals(value));
    }

    private boolean isAdminOrWildcard(User user) {
        return roleStream(user)
                .flatMap(role -> {
                    Stream<String> permissions = role.getPermissions() == null
                            ? Stream.empty()
                            : role.getPermissions().stream();
                    return Stream.concat(Stream.of(role.getCode()), permissions);
                })
                .filter(Objects::nonNull)
                .anyMatch(value -> "SYSTEM_ADMIN".equals(value) || PermissionConstants.WILDCARD.equals(value));
    }

    private Stream<Role> roleStream(User user) {
        if (user == null) {
            return Stream.empty();
        }
        Stream<Role> primary = user.getPrimaryRole() == null
                ? Stream.empty()
                : Stream.of(user.getPrimaryRole());
        Stream<Role> additional = user.getRoles() == null
                ? Stream.empty()
                : user.getRoles().stream();
        return Stream.concat(primary, additional).filter(Objects::nonNull);
    }

    private void trySendPush(NotificationDto notification) {
        try {
            firebasePushNotificationSender.sendToUser(notification);
        } catch (RuntimeException ex) {
            // Push delivery is best-effort; the saved in-app notification remains the source of truth.
            log.warn("Firebase push delivery failed for notification {}: {}", notification.id(), ex.getMessage());
        }
    }
}
