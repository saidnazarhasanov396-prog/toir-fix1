package com.toir.dto.rcm;

import com.toir.entity.RcmSnapshot;

import java.util.List;

public record RcmSnapshotCaptureResponse(
        int createdCount,
        List<RcmSnapshot> items
) {
    public RcmSnapshotCaptureResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
