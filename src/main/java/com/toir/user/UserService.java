package com.toir.user;

import com.toir.common.exception.RestException;
import com.toir.role.Role;
import com.toir.role.RoleRepository;
import com.toir.user.dto.CreateUserRequest;
import com.toir.user.dto.UpdateUserRequest;
import com.toir.user.dto.UserDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAll().stream().map(UserDto::from).toList();
    }

    @Transactional(readOnly = true)
    public UserDto findById(UUID id) {
        return UserDto.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public User findByUsernameForAuth(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> RestException.unauthorized("Invalid credentials"));
    }

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
