package com.toir.entity;

import com.toir.enums.KnowledgeTargetType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "knowledge_article_links",
        indexes = {
                @Index(name = "idx_knowledge_article_links_article", columnList = "knowledge_article_id"),
                @Index(name = "idx_knowledge_article_links_target", columnList = "target_type,target_id")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KnowledgeArticleLink extends BaseEntity {

    @Column(name = "knowledge_article_id", nullable = false)
    private UUID knowledgeArticleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private KnowledgeTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;
}
