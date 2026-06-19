package com.toir.service.users;

import com.toir.dto.user.CreateRoleUserRequest;
import com.toir.dto.user.CreateUserRequest;
import com.toir.dto.user.UpdateUserRequest;
import com.toir.dto.user.UserDto;
import com.toir.dto.user.UserFilterRequest;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.specification.UserSpecifications;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAllWithRolesAndIsDeletedFalse().stream()
                .map(UserDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<UserDto> search(String search, int page, int size) {
        String normalizedSearch = normalizeSearch(search);
        Page<UUID> idsPage = userRepository.searchIds(normalizedSearch, PaginationUtils.pageRequest(page, size));
        if (idsPage.isEmpty()) {
            return Page.empty(idsPage.getPageable());
        }

        Map<UUID, User> usersById = userRepository.findAllWithRolesByIdInAndIsDeletedFalse(idsPage.getContent()).stream()
                .collect(HashMap::new, (map, user) -> map.put(user.getId(), user), HashMap::putAll);

        List<UserDto> content = idsPage.getContent().stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .map(UserDto::from)
                .toList();

        return new PageImpl<>(content, idsPage.getPageable(), idsPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<UserDto> searchWithFilters(UserFilterRequest filter, int page, int size) {
        var pageable = PaginationUtils.pageRequest(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<User> idsPage = userRepository.findAll(UserSpecifications.byFilter(filter), pageable);
        if (idsPage.isEmpty()) {
            return Page.empty(idsPage.getPageable());
        }

        List<UUID> ids = idsPage.getContent().stream()
                .map(User::getId)
                .toList();
        Map<UUID, User> usersById = userRepository.findAllWithRolesByIdInAndIsDeletedFalse(ids).stream()
                .collect(HashMap::new, (map, user) -> map.put(user.getId(), user), HashMap::putAll);

        List<UserDto> content = ids.stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .map(UserDto::from)
                .toList();

        return new PageImpl<>(content, idsPage.getPageable(), idsPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UserDto findById(UUID id) {
        User user = getOrThrow(id);
        Hibernate.initialize(user.getRoles());
        Hibernate.initialize(user.getPrimaryRole());
        if (user.getDepartment() != null) {
            Hibernate.initialize(user.getDepartment());
        }
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

        auditBuilderService.log(
                "user",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.USER,
                "Пользователь создан",
                null,
                saved
        );
        return UserDto.from(saved);
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

        auditBuilderService.log(
                "user",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.USER,
                "Пользователь создан",
                null,
                saved
        );
        return UserDto.from(saved);
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest request) {
        User user = getOrThrow(id);
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

        User save = userRepository.save(user);

        auditBuilderService.log(
                "user",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.USER,
                "Пользователь обновлен",
                user,
                save
        );

        return dto;
    }

    @Transactional
    public void delete(UUID id) {
        User user = getOrThrow(id);
        user.setDeleted(true);
        User saved = userRepository.save(user);


        auditBuilderService.log(
                "user",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.USER,
                "Пользователь удален",
                user,
                null
        );
    }

    @Transactional
    public void touchLastLogin(UUID id) {
        User user = getOrThrow(id);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
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

    private String normalizeSearch(String search) {
        if (search == null) return null;
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }


}
