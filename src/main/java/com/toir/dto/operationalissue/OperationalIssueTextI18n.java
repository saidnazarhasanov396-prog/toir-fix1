package com.toir.dto.operationalissue;

import java.util.Map;

public record OperationalIssueTextI18n(
        String titleKey,
        Map<String, Object> titleParams,
        String messageKey,
        Map<String, Object> messageParams
) {
    public static OperationalIssueTextI18n empty() {
        return new OperationalIssueTextI18n(null, Map.of(), null, Map.of());
    }
}
