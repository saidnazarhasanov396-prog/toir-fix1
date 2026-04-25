package com.toir.service;
import com.toir.entity.User;
import com.toir.repository.UserRepository;

import com.toir.exception.RestException;
import com.toir.entity.Role;
import com.toir.repository.RoleRepository;
import com.toir.dto.user.CreateUserRequest;
import com.toir.dto.user.CreateRoleUserRequest;
import com.toir.dto.user.UpdateUserRequest;
import com.toir.dto.user.UserDto;
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
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;


    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAllWithRoles().stream().map(UserDto::from).toList();
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
        return userRepository.findByUsername(username)
                .orElseThrow(() -> RestException.unauthorized("Invalid credentials"));
    }

    @Transactional
    public UserDto create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw RestException.conflict("Username already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
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
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto createForRole(CreateRoleUserRequest request) {
        String roleCode = request.roleCode();
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> RestException.badRequest("Role not found: " + roleCode));

        String username = roleCode;
        if (userRepository.existsByUsername(username)) {
            throw RestException.conflict("Username already taken: " + username);
        }

        String email = (request.email() != null && !request.email().isBlank())
                ? request.email()
                : roleCode.toLowerCase(Locale.ROOT) + "@toir.local";
        if (userRepository.existsByEmail(email)) {
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
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest request) {
        User user = getOrThrow(id);
        if (!user.getEmail().equals(request.email()) && userRepository.existsByEmail(request.email())) {
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
        return UserDto.from(user);
    }

    public void delete(UUID id) {
        User user = getOrThrow(id);
        userRepository.delete(user);
    }

    public void touchLastLogin(UUID id) {
        User user = getOrThrow(id);
        user.setLastLoginAt(Instant.now());
    }

    private User getOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> RestException.notFound("User not found: " + id));
    }

    private Role resolveRole(UUID roleId) {
        if (roleId == null) return null;
        return roleRepository.findById(roleId)
                .orElseThrow(() -> RestException.badRequest("Role not found: " + roleId));
    }

    private Set<Role> resolveRoles(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return new HashSet<>();
        Set<Role> found = new HashSet<>(roleRepository.findAllById(roleIds));
        if (found.size() != roleIds.size()) {
            throw RestException.badRequest("One or more roles not found");
        }
        return found;
    }
}
