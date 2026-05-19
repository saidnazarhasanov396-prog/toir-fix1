package com.toir.security;

import java.util.Optional;
import java.util.UUID;

public interface ScopeObjectResolver {

    default Optional<UUID> resolveDepartmentId(UUID objectId) {
        return Optional.empty();
    }

    default Optional<UUID> resolveWarehouseId(UUID objectId) {
        return Optional.empty();
    }

    default Optional<UUID> resolveOwnerUserId(UUID objectId) {
        return Optional.empty();
    }

    default Optional<UUID> resolveAssignedUserId(UUID objectId) {
        return Optional.empty();
    }
}
