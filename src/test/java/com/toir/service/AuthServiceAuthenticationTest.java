package com.toir.service;

import com.toir.dto.auth.LoginRequest;
import com.toir.entity.users.User;
import com.toir.enums.UserStatus;
import com.toir.exception.RestException;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.JwtService;
import com.toir.util.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceAuthenticationTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuditLogService auditLogService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        auditLogService = mock(AuditLogService.class);
        service = new AuthService(
                userRepository,
                mock(RoleRepository.class),
                passwordEncoder,
                jwtService,
                auditLogService,
                mock(RequestContext.class)
        );
    }

    @Test
    void unknownUserGetsTheGenericUnauthorizedResponse() {
        when(userRepository.findByUsernameAndIsDeletedFalse("missing"))
                .thenReturn(Optional.empty());

        assertRestFailure("missing", "wrong-password", HttpStatus.UNAUTHORIZED, "Invalid credentials");

        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }

    @Test
    void wrongPasswordUsesTheSameGenericUnauthorizedResponse() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findByUsernameAndIsDeletedFalse("user"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        assertRestFailure("user", "wrong-password", HttpStatus.UNAUTHORIZED, "Invalid credentials");

        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }

    @Test
    void inactiveUserIsRejectedBeforeTokenGeneration() {
        User user = user(UserStatus.INACTIVE);
        when(userRepository.findByUsernameAndIsDeletedFalse("user"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", user.getPasswordHash())).thenReturn(true);

        assertRestFailure("user", "password", HttpStatus.FORBIDDEN, "User is not active");

        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }

    @Test
    void suspendedUserIsRejectedBeforeTokenGeneration() {
        User user = user(UserStatus.SUSPENDED);
        when(userRepository.findByUsernameAndIsDeletedFalse("user"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", user.getPasswordHash())).thenReturn(true);

        assertRestFailure("user", "password", HttpStatus.FORBIDDEN, "User is not active");

        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }

    private void assertRestFailure(String username, String password, HttpStatus status, String message) {
        assertThatThrownBy(() -> service.login(new LoginRequest(username, password)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getMessage()).isEqualTo(message);
                });
    }

    private User user(UserStatus status) {
        User user = new User();
        user.setUsername("user");
        user.setPasswordHash("test-password-hash");
        user.setStatus(status);
        return user;
    }
}
