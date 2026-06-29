package com.toir.dto.warehouse;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WarehouseTaskAssignRequest(@NotNull UUID assignedToId) {
}
