package com.toir.service.users;
import com.toir.entity.users.User;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.users.UserRepository;

import com.toir.exception.RestException;
import com.toir.entity.users.Role;
import com.toir.repository.users.RoleRepository;
import com.toir.dto.user.CreateUserRequest;
import com.toir.dto.user.CreateRoleUserRequest;
import com.toir.dto.user.UpdateUserRequest;
import com.toir.dto.user.UserDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAllWithRolesAndIsDeletedFalse().stream()
                .map(UserDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDto findById(UUID id) {
        User user = getOrThrow(id);
        Hibernate.initialize(user.getRoles());
        Hibernate.initialize(user.getPrimaryRole());
        return UserDto.from(user);
    }

    @Transactional(readOnly = true)
    public User findByUsernameForAuth(String username) {
        return userRepository.findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() -> RestException.unauthorized("Invalid credentials"));
    }

    @Transactional
    public UserDto create(CreateUserRequest request) {
        if (userRepository.existsByUsernameAndIsDeletedFalse(request.username())) {
            throw RestException.conflict("Username already taken");
        }
        if (userRepository.existsByEmailAndIsDeletedFalse(request.email())) {
            throw RestException.conflict("Email already taken");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPosition(request.position());
        user.setPhone(request.phone());
        user.setDepartmentId(request.departmentId());
        user.setPrimaryRole(resolveRole(request.primaryRoleId()));
        user.setRoles(resolveRoles(request.roleIds()));
        User saved = userRepository.save(user);
        UserDto dto = UserDto.from(saved);
        audit(AuditAction.CREATE, saved.getId(), null, dto);
        return dto;
    }

    @Transactional
    public UserDto createForRole(CreateRoleUserRequest request) {
        String roleCode = request.roleCode();
        Role role = roleRepository.findByCodeAndIsDeletedFalse(roleCode)
                .orElseThrow(() -> RestException.badRequest("Role not found: " + roleCode));

        String username = roleCode;
        if (userRepository.existsByUsernameAndIsDeletedFalse(username)) {
            throw RestException.conflict("Username already taken: " + username);
        }

        String email = (request.email() != null && !request.email().isBlank())
                ? request.email()
                : roleCode.toLowerCase(Locale.ROOT) + "@toir.local";
        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw RestException.conflict("Email already taken: " + email);
        }

        String fullName = (request.fullName() != null && !request.fullName().isBlank())
                ? request.fullName()
                : (role.getNameEn() != null && !role.getNameEn().isBlank() ? role.getNameEn() : roleCode);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPosition(request.position());
        user.setPhone(request.phone());
        user.setDepartmentId(request.departmentId());
        user.setPrimaryRole(role);
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);
        User saved = userRepository.save(user);
        UserDto dto = UserDto.from(saved);
        audit(AuditAction.CREATE, saved.getId(), null, dto);
        return dto;
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest request) {
        User user = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(UserDto.from(user));
        if (!user.getEmail().equals(request.email()) && userRepository.existsByEmailAndIsDeletedFalse(request.email())) {
            throw RestException.conflict("Email already taken");
        }
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPosition(request.position());
        user.setPhone(request.phone());
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        user.setDepartmentId(request.departmentId());
        user.setPrimaryRole(resolveRole(request.primaryRoleId()));
        user.setRoles(resolveRoles(request.roleIds()));
        UserDto dto = UserDto.from(user);
        audit(AuditAction.UPDATE, user.getId(), oldJson, dto);
        return dto;
    }

    public void delete(UUID id) {
        User user = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(UserDto.from(user));
        user.setDeleted(true);
        User saved = userRepository.save(user);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    public void touchLastLogin(UUID id) {
        User user = getOrThrow(id);
        user.setLastLoginAt(Instant.now());
    }

    private User getOrThrow(UUID id) {
        return userRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("User not found: " + id));
    }

    private Role resolveRole(UUID roleId) {
        if (roleId == null) return null;
        return roleRepository.findByIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> RestException.badRequest("Role not found: " + roleId));
    }

    private Set<Role> resolveRoles(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return new HashSet<>();
        Set<Role> found = new HashSet<>(roleRepository.findAllByIdInAndIsDeletedFalse(roleIds));
        if (found.size() != roleIds.size()) {
            throw RestException.badRequest("One or more roles not found");
        }
        return found;
    }

    private void audit(AuditAction action, UUID id, String oldJson, UserDto current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "user",
                id != null ? id.toString() : null,
                action,
                AuditModule.USER,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Пользователь создан";
            case UPDATE -> "Пользователь обновлен";
            case DELETE -> "Пользователь удален";
            default -> "Действие выполнено над пользователем";
        };
    }
}
