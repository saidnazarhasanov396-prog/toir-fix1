package com.toir.dto.equipmentlifecycle;

import java.util.List;
import java.util.UUID;

public record EquipmentLifecycleDataQuality(
        List<QualityIssue> issues,
        List<String> missingCriticalFields
) {
    public EquipmentLifecycleDataQuality {
        issues = issues == null ? List.of() : List.copyOf(issues);
        missingCriticalFields = missingCriticalFields == null
                ? List.of()
                : List.copyOf(missingCriticalFields);
    }

    public static EquipmentLifecycleDataQuality empty() {
        return new EquipmentLifecycleDataQuality(List.of(), List.of());
    }

    public enum Availability {
        AVAILABLE_AND_POPULATED,
        AVAILABLE_BUT_OPTIONAL,
        AVAILABLE_BUT_UNRELIABLE,
        DERIVABLE,
        MISSING,
        REQUIRES_DOCUMENT_PARSING,
        OUT_OF_SCOPE_FOR_V1
    }

    public enum SourceReliability {
        RELIABLE,
        OPTIONAL,
        UNRELIABLE,
        DERIVED,
        MISSING,
        PARSER_REQUIRED,
        OUT_OF_SCOPE
    }

    public enum IssueSeverity {
        INFO,
        WARNING,
        ERROR
    }

    public record QualityIssue(
            String section,
            String code,
            IssueSeverity severity,
            String summary,
            List<UUID> affectedSourceIds
    ) {
        public QualityIssue {
            affectedSourceIds = affectedSourceIds == null
                    ? List.of()
                    : List.copyOf(affectedSourceIds);
        }
    }
}
