package com.toir.config;

import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActorAuditorAware implements AuditorAware<UUID> {

    private final SecurityScope securityScope;

    @Override
    public Optional<UUID> getCurrentAuditor() {
        AuthenticatedUser user = securityScope.currentUser();
        if (user == null || user.id() == null || user.id().isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(user.id()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
