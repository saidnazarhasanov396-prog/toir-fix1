package com.toir.service;

import com.toir.dto.notification.NotificationDto;
import com.toir.dto.notification.NotificationContent;
import com.toir.entity.Notification;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.enums.UserStatus;
import com.toir.repository.NotificationRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository repository;

    @Mock
    UserRepository userRepository;

    @Mock
    EmployeeRepository employeeRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    FirebasePushNotificationSender firebasePushNotificationSender;

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

    @Test
    void notifyUserPersistsRussianUzbekAndEnglishContent() {
        UUID recipientId = UUID.randomUUID();
        String entityId = UUID.randomUUID().toString();
        NotificationContent content = new NotificationContent(
                "Русский заголовок",
                "Русский текст",
                "O‘zbekcha sarlavha",
                "O‘zbekcha matn",
                "English title",
                "English body"
        );
        when(repository.existsOpenForRecipientAndEntity(
                recipientId,
                "WORK_ORDER",
                entityId,
                content.titleRu()
        )).thenReturn(false);
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.randomUUID());
            return notification;
        });

        var result = service.notifyUser(
                recipientId,
                content,
                NotificationSeverity.INFO,
                "WORK_ORDER",
                entityId
        );

        assertThat(result).isPresent();
        org.mockito.ArgumentCaptor<Notification> captor =
                org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo(content.titleRu());
        assertThat(saved.getMessage()).isEqualTo(content.messageRu());
        assertThat(saved.getTitleUz()).isEqualTo(content.titleUz());
        assertThat(saved.getMessageUz()).isEqualTo(content.messageUz());
        assertThat(saved.getTitleEn()).isEqualTo(content.titleEn());
        assertThat(saved.getMessageEn()).isEqualTo(content.messageEn());
    }

    @Test
    void notifyUserSkipsDuplicateOpenNotificationForSameWorkflowEvent() {
        UUID recipientId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.existsOpenForRecipientAndEntity(
                recipientId,
                "WorkOrder",
                entityId.toString(),
                "Approval requested"
        )).thenReturn(true);

        var result = service.notifyUser(
                recipientId,
                "Approval requested",
                "Work order requires approval",
                NotificationSeverity.INFO,
                "WorkOrder",
                entityId.toString()
        );

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void notifyDepartmentByPermissionPrefersDepartmentNonAdminRecipients() {
        UUID departmentId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        User maintainer = user(UUID.randomUUID(), departmentId, "MAINTENANCE_FOREMAN",
                List.of(PermissionConstants.REPAIR_REQUEST_ASSIGN));
        User admin = user(UUID.randomUUID(), departmentId, "SYSTEM_ADMIN", List.of(PermissionConstants.WILDCARD));
        when(userRepository.findAllWithRolesAndIsDeletedFalse()).thenReturn(List.of(admin, maintainer));
        when(repository.existsOpenForRecipientAndEntity(
                eq(maintainer.getId()),
                eq("RepairRequest"),
                eq(entityId.toString()),
                eq("Repair request created")
        )).thenReturn(false);
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.randomUUID());
            return notification;
        });

        List<NotificationDto> result = service.notifyDepartmentByPermission(
                departmentId,
                PermissionConstants.REPAIR_REQUEST_ASSIGN,
                "Repair request created",
                "New request needs assignment",
                NotificationSeverity.INFO,
                "RepairRequest",
                entityId.toString()
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().recipientId()).isEqualTo(maintainer.getId());
        verify(repository, never()).existsOpenForRecipientAndEntity(
                eq(admin.getId()),
                any(),
                any(),
                any()
        );
    }

    private User user(UUID id, UUID departmentId, String roleCode, List<String> permissions) {
        Role role = new Role();
        role.setCode(roleCode);
        role.setPermissions(permissions);

        User user = new User();
        user.setId(id);
        user.setUsername(roleCode.toLowerCase());
        user.setEmail(roleCode.toLowerCase() + "@example.test");
        user.setFullName(roleCode);
        user.setStatus(UserStatus.ACTIVE);
        user.setDepartmentId(departmentId);
        user.setPrimaryRole(role);
        user.setRoles(new HashSet<>(List.of(role)));
        return user;
    }
}
