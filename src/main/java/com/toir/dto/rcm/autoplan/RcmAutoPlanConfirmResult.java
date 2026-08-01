package com.toir.dto.rcm.autoplan;

import java.util.List;

public record RcmAutoPlanConfirmResult(
        int candidates,
        int tasksCreated,
        int duplicates,
        int conflicts,
        int skipped,
        String fingerprint,
        List<String> createdCodes
) {
    public RcmAutoPlanConfirmResult {
        createdCodes = createdCodes == null ? List.of() : List.copyOf(createdCodes);
    }
}
