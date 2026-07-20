package com.toir.dto.analytics;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record DashboardDetailResponse(
        String schemaVersion, String moduleCode, long sourceRevision, Instant generatedAt,
        String nextCursor, boolean hasMore, List<DetailRecord> records
) {
    public static DashboardDetailResponse page(String module, long revision, List<DetailRecord> rows, String cursor, int limit) {
        int offset = decode(cursor, revision);
        if (offset > rows.size()) throw error(HttpStatus.BAD_REQUEST, "Cursor is out of range");
        int end = Math.min(rows.size(), offset + limit);
        boolean more = end < rows.size();
        return new DashboardDetailResponse(
                "1.0", module, revision, revision == 0 ? Instant.EPOCH : Instant.ofEpochMilli(revision),
                more ? encode(revision, end) : null, more, List.copyOf(rows.subList(offset, end)));
    }

    private static int decode(String cursor, long revision) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", 2);
            if (Long.parseLong(parts[0]) != revision) throw error(HttpStatus.CONFLICT, "Snapshot changed");
            int value = Integer.parseInt(parts[1]);
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException exception) {
            throw error(HttpStatus.BAD_REQUEST, "Cursor is invalid");
        }
    }

    private static String encode(long revision, int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (revision + ":" + offset).getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    public record DetailRecord(
            String datasetType, String recordId, long recordRevision, String operation, UUID siteId,
            Instant eventAt, Instant updatedAt, Map<String, Object> payload
    ) {}
}
