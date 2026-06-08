package com.toir.dto.safetychecklist;

import com.toir.enums.SafetyChecklistItemStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SafetyChecklistItemUpdateRequest(
        @NotNull SafetyChecklistItemStatus status,
        String comment,
        UUID checkedById
) {
}
