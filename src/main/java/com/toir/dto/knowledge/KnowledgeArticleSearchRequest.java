package com.toir.dto.knowledge;

import com.toir.enums.KnowledgeTargetType;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record KnowledgeArticleSearchRequest(
        String q,
        List<String> kinds,
        KnowledgeTargetType targetType,
        UUID targetId,
        UUID equipmentId,
        UUID equipmentTypeId,
        UUID defectId,
        UUID workOrderId,
        List<String> tags,
        Instant createdFrom,
        Instant createdTo,
        Instant updatedFrom,
        Instant updatedTo,
        Boolean hasLinks,
        int page,
        int size,
        String sort
) {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "updatedAt,desc";
    private static final List<String> ALLOWED_SORTS = List.of(
            DEFAULT_SORT,
            "createdAt,desc",
            "viewCount,desc",
            "title,asc",
            "code,asc",
            "kind,asc"
    );

    public static KnowledgeArticleSearchRequest legacy(UUID equipmentId,
                                                       UUID equipmentTypeId,
                                                       String kind,
                                                       int page,
                                                       int size) {
        return new KnowledgeArticleSearchRequest(
                null,
                kind == null || kind.isBlank() ? List.of() : List.of(kind),
                null,
                null,
                equipmentId,
                equipmentTypeId,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                page,
                size,
                DEFAULT_SORT
        ).normalized();
    }

    public KnowledgeArticleSearchRequest normalized() {
        return new KnowledgeArticleSearchRequest(
                blankToNull(q),
                normalizeValues(kinds),
                targetType,
                targetId,
                equipmentId,
                equipmentTypeId,
                defectId,
                workOrderId,
                normalizeValues(tags).stream()
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .distinct()
                        .toList(),
                createdFrom,
                createdTo,
                updatedFrom,
                updatedTo,
                hasLinks,
                Math.max(0, page),
                Math.max(1, Math.min(size <= 0 ? DEFAULT_SIZE : size, MAX_SIZE)),
                normalizeSort(sort)
        );
    }

    public boolean kindsEmpty() {
        return kinds == null || kinds.isEmpty();
    }

    public List<String> repositoryKinds() {
        return kindsEmpty() ? List.of("__NO_KIND__") : kinds;
    }

    public boolean tagsEmpty() {
        return tags == null || tags.isEmpty();
    }

    public List<String> repositoryTags() {
        return tagsEmpty() ? List.of("__no_tag__") : tags;
    }

    public String targetTypeValue() {
        return targetType == null ? null : targetType.name();
    }

    public String sortValue() {
        return sort == null ? DEFAULT_SORT : sort;
    }

    private static List<String> normalizeValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeSort(String value) {
        String normalized = blankToNull(value);
        if (normalized == null || !ALLOWED_SORTS.contains(normalized)) {
            return DEFAULT_SORT;
        }
        return normalized;
    }
}
