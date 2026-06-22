package com.toir.dto.knowledge;

import com.toir.entity.KnowledgeArticleLink;
import com.toir.enums.KnowledgeTargetType;

import java.util.UUID;

public record KnowledgeArticleLinkDto(
        UUID id,
        KnowledgeTargetType targetType,
        UUID targetId
) {
    public static KnowledgeArticleLinkDto from(KnowledgeArticleLink link) {
        return new KnowledgeArticleLinkDto(
                link.getId(),
                link.getTargetType(),
                link.getTargetId()
        );
    }
}
