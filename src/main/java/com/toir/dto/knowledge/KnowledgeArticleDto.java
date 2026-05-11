package com.toir.dto.knowledge;

import com.toir.entity.KnowledgeArticle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeArticleDto(
        UUID id,
        String code,
        String title,
        String kind,
        UUID equipmentTypeId,
        UUID equipmentId,
        UUID defectId,
        UUID workOrderId,
        String problem,
        String rootCause,
        String solution,
        String preventiveActions,
        List<String> tags,
        UUID authorId,
        int viewCount,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted
) {
    public static KnowledgeArticleDto from(KnowledgeArticle article) {
        return new KnowledgeArticleDto(
                article.getId(),
                article.getCode(),
                article.getTitle(),
                article.getKind(),
                article.getEquipmentTypeId(),
                article.getEquipmentId(),
                article.getDefectId(),
                article.getWorkOrderId(),
                article.getProblem(),
                article.getRootCause(),
                article.getSolution(),
                article.getPreventiveActions(),
                article.getTags() == null ? List.of() : article.getTags(),
                article.getAuthorId(),
                article.getViewCount(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                article.isDeleted()
        );
    }
}
