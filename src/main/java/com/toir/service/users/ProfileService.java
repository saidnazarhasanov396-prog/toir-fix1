package com.toir.service.users;

import com.toir.dto.file.FileResponse;
import com.toir.dto.file.UploadFileResponse;
import com.toir.dto.profile.ChangePasswordRequest;
import com.toir.dto.profile.ChangePasswordResponse;
import com.toir.dto.profile.ProfileDepartmentResponse;
import com.toir.dto.profile.ProfileResponse;
import com.toir.dto.profile.UpdateProfileRequest;
import com.toir.entity.Department;
import com.toir.entity.users.Employee;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.FileCategory;
import com.toir.exception.RestException;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.AuditLogService;
import com.toir.service.file_management.FileService;
import com.toir.util.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final String AVATAR_URL = "/api/v1/profile/avatar";

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileAvatarValidator avatarValidator;
    private final ProfileAvatarStorageService avatarStorageService;
    private final FileService fileService;
    private final AuditLogService auditLogService;
    private final RequestContext requestContext;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID currentUserId) {
        return toResponse(loadUser(currentUserId), loadEmployee(currentUserId));
    }

    @Transactional
    public ProfileResponse updateProfile(UUID currentUserId, UpdateProfileRequest request) {
        User user = loadUser(currentUserId);
        Optional<Employee> employee = loadEmployee(currentUserId);
        String firstName = normalizeName(request.firstName());
        String lastName = normalizeName(request.lastName());

        employee.ifPresent(value -> {
            value.setFirstName(firstName);
            value.setLastName(lastName);
            employeeRepository.save(value);
        });

        user.setFullName(displayName(firstName, lastName));
        userRepository.save(user);
        audit(
                currentUserId,
                AuditAction.UPDATE,
                "Пользователь обновил имя и фамилию профиля"
        );
        return toResponse(user, employee);
    }

    @Transactional
    public ProfileResponse uploadAvatar(UUID currentUserId, MultipartFile file) {
        avatarValidator.validate(file);
        User user = loadUser(currentUserId);
        UUID previousAvatarId = activeAvatarId(user);

        UploadFileResponse uploaded = avatarStorageService.upload(file, currentUserId);
        registerAvatarLifecycle(uploaded.id(), previousAvatarId, currentUserId);

        user.setAvatarFileId(uploaded.id());
        userRepository.saveAndFlush(user);
        audit(currentUserId, AuditAction.UPDATE, "Пользователь обновил фотографию профиля");
        return toResponse(user, loadEmployee(currentUserId), uploaded.id().toString());
    }

    @Transactional
    public void deleteAvatar(UUID currentUserId) {
        User user = loadUser(currentUserId);
        UUID avatarId = activeAvatarId(user);
        if (avatarId == null) {
            return;
        }

        user.setAvatarFileId(null);
        userRepository.saveAndFlush(user);
        registerDeleteAfterCommit(avatarId, currentUserId);
        audit(currentUserId, AuditAction.DELETE, "Пользователь удалил фотографию профиля");
    }

    @Transactional(readOnly = true)
    public AvatarDownload downloadAvatar(UUID currentUserId) {
        User user = loadUser(currentUserId);
        UUID avatarId = activeAvatarId(user);
        if (avatarId == null) {
            throw RestException.notFound("Profile avatar not found");
        }
        FileResponse metadata = fileService.getMetadata(avatarId, currentUserId);
        if (metadata.category() != FileCategory.USER_AVATAR) {
            throw RestException.notFound("Profile avatar not found");
        }
        return new AvatarDownload(
                fileService.download(avatarId, currentUserId),
                MediaType.parseMediaType(metadata.contentType()),
                metadata.originalName()
        );
    }

    @Transactional
    public ChangePasswordResponse changePassword(UUID currentUserId, ChangePasswordRequest request) {
        User user = loadUser(currentUserId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw RestException.badRequest(
                    "Current password is incorrect",
                    "PROFILE_CURRENT_PASSWORD_INVALID"
            );
        }
        if (!Objects.equals(request.newPassword(), request.confirmPassword())) {
            throw RestException.badRequest(
                    "New password and confirmation do not match",
                    "PROFILE_PASSWORD_CONFIRMATION_MISMATCH"
            );
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw RestException.badRequest(
                    "New password must differ from the current password",
                    "PROFILE_PASSWORD_UNCHANGED"
            );
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        audit(currentUserId, AuditAction.UPDATE, "Пользователь изменил пароль");
        return new ChangePasswordResponse("Password changed successfully", false);
    }

    private User loadUser(UUID currentUserId) {
        if (currentUserId == null) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        return userRepository.findProfileById(currentUserId)
                .orElseThrow(() -> RestException.unauthorized("Authenticated user is unavailable"));
    }

    private Optional<Employee> loadEmployee(UUID currentUserId) {
        return employeeRepository.findByUserIdAndIsDeletedFalse(currentUserId);
    }

    private ProfileResponse toResponse(User user, Optional<Employee> employee) {
        return toResponse(user, employee, activeAvatarId(user) == null ? null : activeAvatarId(user).toString());
    }

    private ProfileResponse toResponse(User user, Optional<Employee> employee, String avatarVersion) {
        NameParts names = employee
                .map(value -> new NameParts(value.getFirstName(), value.getLastName()))
                .orElseGet(() -> splitFullName(user.getFullName()));
        UUID departmentId = user.getDepartmentId() != null
                ? user.getDepartmentId()
                : employee.map(Employee::getDepartmentId).orElse(null);
        ProfileDepartmentResponse department = departmentId == null
                ? null
                : departmentRepository.findByIdAndIsDeletedFalse(departmentId)
                        .map(this::toDepartment)
                        .orElse(null);

        Set<String> roles = new LinkedHashSet<>();
        if (user.getPrimaryRole() != null) {
            roles.add(user.getPrimaryRole().getCode());
        }
        if (user.getRoles() != null) {
            roles.addAll(user.getRoles().stream().map(Role::getCode).filter(Objects::nonNull).toList());
        }

        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                names.firstName(),
                names.lastName(),
                displayName(names.firstName(), names.lastName()),
                avatarVersion == null ? null : AVATAR_URL,
                avatarVersion,
                department,
                List.copyOf(roles)
        );
    }

    private ProfileDepartmentResponse toDepartment(Department department) {
        return new ProfileDepartmentResponse(department.getId(), department.getName());
    }

    private UUID activeAvatarId(User user) {
        if (user.getAvatarFileId() == null) {
            return null;
        }
        if (user.getAvatarFile() == null) {
            return user.getAvatarFileId();
        }
        return Boolean.FALSE.equals(user.getAvatarFile().getDeleted())
                && user.getAvatarFile().getCategory() == FileCategory.USER_AVATAR
                && user.getId().equals(user.getAvatarFile().getUploadedBy())
                ? user.getAvatarFileId()
                : null;
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw RestException.badRequest("First name and last name must not be blank");
        }
        return normalized;
    }

    private NameParts splitFullName(String fullName) {
        String normalized = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return new NameParts("", "");
        }
        int separator = normalized.indexOf(' ');
        return separator < 0
                ? new NameParts(normalized, "")
                : new NameParts(normalized.substring(0, separator), normalized.substring(separator + 1));
    }

    private String displayName(String firstName, String lastName) {
        return (firstName + " " + lastName).trim();
    }

    private void registerAvatarLifecycle(UUID newAvatarId, UUID previousAvatarId, UUID currentUserId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(previousAvatarId, currentUserId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(previousAvatarId, currentUserId);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(newAvatarId, currentUserId);
                }
            }
        });
    }

    private void registerDeleteAfterCommit(UUID avatarId, UUID currentUserId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(avatarId, currentUserId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(avatarId, currentUserId);
            }
        });
    }

    private void deleteQuietly(UUID fileId, UUID currentUserId) {
        if (fileId == null) {
            return;
        }
        try {
            avatarStorageService.deleteOwnedFile(fileId, currentUserId);
        } catch (RuntimeException exception) {
            log.warn("Could not clean up profile avatar file '{}': {}", fileId, exception.getMessage());
        }
    }

    private void audit(UUID userId, AuditAction action, String message) {
        auditLogService.record(
                userId,
                AuditModule.USER,
                "UserProfile",
                userId.toString(),
                action,
                message,
                requestContext.getIpAddress(),
                requestContext.getUserAgent()
        );
    }

    public record AvatarDownload(Resource resource, MediaType contentType, String originalName) {
    }

    private record NameParts(String firstName, String lastName) {
    }
}
