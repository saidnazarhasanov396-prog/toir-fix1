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
        List<KnowledgeArticleLinkDto> links,
        UUID authorId,
        int viewCount,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted
) {
    public KnowledgeArticleDto(
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
        this(
                id,
                code,
                title,
                kind,
                equipmentTypeId,
                equipmentId,
                defectId,
                workOrderId,
                problem,
                rootCause,
                solution,
                preventiveActions,
                tags,
                List.of(),
                authorId,
                viewCount,
                createdAt,
                updatedAt,
                deleted
        );
    }

    public static KnowledgeArticleDto from(KnowledgeArticle article) {
        return from(article, article.getLinks() == null ? List.of() : article.getLinks());
    }

    public static KnowledgeArticleDto from(KnowledgeArticle article, List<KnowledgeArticleLinkDto> links) {
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
                links == null ? List.of() : links,
                article.getAuthorId(),
                article.getViewCount(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                article.isDeleted()
        );
    }
}
