package com.toir.service.users;

import com.toir.dto.profile.ChangePasswordRequest;
import com.toir.dto.profile.ProfileResponse;
import com.toir.dto.profile.UpdateProfileRequest;
import com.toir.dto.file.UploadFileResponse;
import com.toir.entity.Department;
import com.toir.entity.users.Employee;
import com.toir.entity.users.User;
import com.toir.exception.RestException;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.AuditLogService;
import com.toir.service.file_management.FileService;
import com.toir.util.RequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock UserRepository userRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ProfileAvatarValidator avatarValidator;
    @Mock ProfileAvatarStorageService avatarStorageService;
    @Mock FileService fileService;
    @Mock AuditLogService auditLogService;
    @Mock RequestContext requestContext;

    @InjectMocks ProfileService service;

    @Test
    void returnsLegacyUserWithoutEmployeeOrDepartment() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "System Administrator");
        when(userRepository.findProfileById(userId)).thenReturn(Optional.of(user));
        when(employeeRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.empty());

        ProfileResponse response = service.getProfile(userId);

        assertThat(response.firstName()).isEqualTo("System");
        assertThat(response.lastName()).isEqualTo("Administrator");
        assertThat(response.department()).isNull();
        assertThat(response.avatarUrl()).isNull();
    }

    @Test
    void returnsAccountDepartmentWhenAssigned() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        User user = user(userId, "Profile User");
        user.setDepartmentId(departmentId);
        Department department = new Department();
        department.setId(departmentId);
        department.setName("Mechanical Service");
        when(userRepository.findProfileById(userId)).thenReturn(Optional.of(user));
        when(employeeRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.empty());
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId))
                .thenReturn(Optional.of(department));

        ProfileResponse response = service.getProfile(userId);

        assertThat(response.department()).isNotNull();
        assertThat(response.department().id()).isEqualTo(departmentId);
        assertThat(response.department().name()).isEqualTo("Mechanical Service");
    }

    @Test
    void updatesEmployeeCanonicalNamesAndUserDisplayCopyTogether() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "Old Name");
        Employee employee = new Employee();
        employee.setFirstName("Old");
        employee.setLastName("Name");
        when(userRepository.findProfileById(userId)).thenReturn(Optional.of(user));
        when(employeeRepository.findByUserIdAndIsDeletedFalse(userId))
                .thenReturn(Optional.of(employee));

        ProfileResponse response = service.updateProfile(
                userId,
                new UpdateProfileRequest("  Иван  ", "  Иванов  ")
        );

        assertThat(employee.getFirstName()).isEqualTo("Иван");
        assertThat(employee.getLastName()).isEqualTo("Иванов");
        assertThat(user.getFullName()).isEqualTo("Иван Иванов");
        assertThat(response.displayName()).isEqualTo("Иван Иванов");
        verify(employeeRepository).save(employee);
        verify(userRepository).save(user);
    }

    @Test
    void rejectsIncorrectCurrentPasswordWithoutChangingHash() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "User Name");
        user.setPasswordHash("existing-hash");
        when(userRepository.findProfileById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "existing-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(
                userId,
                new ChangePasswordRequest("wrong-password", "new-password", "new-password")
        )).isInstanceOf(RestException.class);

        assertThat(user.getPasswordHash()).isEqualTo("existing-hash");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changesPasswordUsingConfiguredEncoderAndDoesNotRequireRelogin() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "User Name");
        user.setPasswordHash("existing-hash");
        when(userRepository.findProfileById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "existing-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "existing-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        var response = service.changePassword(
                userId,
                new ChangePasswordRequest("current-password", "new-password", "new-password")
        );

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(response.reauthenticationRequired()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void uploadsAvatarBeforePublishingItsUserReference() {
        UUID userId = UUID.randomUUID();
        UUID avatarId = UUID.randomUUID();
        User user = user(userId, "Profile User");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[] { 1 }
        );
        when(userRepository.findProfileById(userId)).thenReturn(Optional.of(user));
        when(employeeRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.empty());
        when(avatarStorageService.upload(file, userId))
                .thenReturn(UploadFileResponse.builder().id(avatarId).build());

        ProfileResponse response = service.uploadAvatar(userId, file);

        assertThat(user.getAvatarFileId()).isEqualTo(avatarId);
        assertThat(response.avatarUrl()).isEqualTo("/api/v1/profile/avatar");
        assertThat(response.avatarVersion()).isEqualTo(avatarId.toString());
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void deleteAvatarClearsReferenceAndDeletesOnlyOwnedFile() {
        UUID userId = UUID.randomUUID();
        UUID avatarId = UUID.randomUUID();
        User user = user(userId, "Profile User");
        user.setAvatarFileId(avatarId);
        when(userRepository.findProfileById(userId)).thenReturn(Optional.of(user));

        service.deleteAvatar(userId);

        assertThat(user.getAvatarFileId()).isNull();
        verify(userRepository).saveAndFlush(user);
        verify(avatarStorageService).deleteOwnedFile(avatarId, userId);
    }

    private User user(UUID id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setUsername("profile.user");
        user.setEmail("profile.user@example.com");
        user.setFullName(fullName);
        user.setRoles(new HashSet<>());
        return user;
    }
}
