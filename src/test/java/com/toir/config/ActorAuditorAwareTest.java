package com.toir.config;

import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActorAuditorAwareTest {

    private final SecurityScope securityScope = mock(SecurityScope.class);
    private final ActorAuditorAware auditorAware = new ActorAuditorAware(securityScope);

    @Test
    void returnsCurrentAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        when(securityScope.currentUser()).thenReturn(user(userId.toString()));

        assertThat(auditorAware.getCurrentAuditor()).contains(userId);
    }

    @Test
    void returnsEmptyWhenNoCurrentUserExists() {
        when(securityScope.currentUser()).thenReturn(null);

        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void returnsEmptyWhenCurrentUserIdIsNotUuid() {
        when(securityScope.currentUser()).thenReturn(user("system"));

        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    private AuthenticatedUser user(String id) {
        return new AuthenticatedUser(id, "user", "user@example.com", "User", null, "USER", List.of());
    }
}
