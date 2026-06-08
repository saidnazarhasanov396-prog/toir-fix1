package com.toir.dto.safetychecklist;

import java.util.UUID;

public record SafetyChecklistDecisionRequest(
        UUID checkedById,
        String remarks
) {
}
