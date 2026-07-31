package com.toir.dto.equipmentlifecycleexport;

import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class EquipmentLifecycleExportRequests {
    private EquipmentLifecycleExportRequests() {}

    public record CreateRequest(
            @NotBlank String profile,
            @NotNull @Valid ScopeRequest scope
    ) {}

    public record ScopeRequest(
            @NotNull EquipmentLifecycleExportScopeMode mode,
            List<UUID> equipmentIds
    ) {}
}
