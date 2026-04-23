package com.toir.service;

import com.toir.entity.AuditAction;
import com.toir.service.AuditLogService;
import com.toir.dto.auth.LoginRequest;
import com.toir.dto.auth.LoginResponse;
import com.toir.util.RequestContext;
import com.toir.exception.RestException;
import com.toir.security.AuthenticatedUser;
import com.toir.security.JwtService;
import com.toir.entity.Role;
import com.toir.entity.User;
import com.toir.repository.UserRepository;
import com.toir.entity.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final RequestContext requestContext;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditLogService auditLogService, RequestContext requestContext) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.requestContext = requestContext;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> RestException.unauthorized("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw RestException.unauthorized("Invalid credentials");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw RestException.forbidden("User is not active");
        }

        Set<String> permissions = new LinkedHashSet<>();
        List<String> authorities = user.getRoles().stream().map(Role::getCode).toList();
        for (Role role : user.getRoles()) {
            if (role.getPermissions() != null) permissions.addAll(role.getPermissions());
        }
        if (user.getPrimaryRole() != null && user.getPrimaryRole().getPermissions() != null) {
            permissions.addAll(user.getPrimaryRole().getPermissions());
        }

        String primaryRoleCode = user.getPrimaryRole() != null ? user.getPrimaryRole().getCode() : null;
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId().toString(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getDepartmentId() != null ? user.getDepartmentId().toString() : null,
                primaryRoleCode,
                List.copyOf(permissions)
        );

        Map<String, Object> extra = new HashMap<>();
        extra.put("email", user.getEmail());
        extra.put("fullName", user.getFullName());
        extra.put("departmentId", principal.departmentId());
        extra.put("primaryRoleCode", primaryRoleCode);
        extra.put("permissions", principal.permissions());

        String token = jwtService.generateToken(principal.id(), principal.username(), authorities, extra);

        user.setLastLoginAt(Instant.now());

        auditLogService.record(
                user.getId(),
                "auth",
                "User",
                user.getId().toString(),
                AuditAction.LOGIN,
                "User " + user.getUsername() + " logged in",
                requestContext.getIpAddress(),
                requestContext.getUserAgent()
        );

        return new LoginResponse(token, jwtService.getExpirationSeconds(), principal);
    }
}
