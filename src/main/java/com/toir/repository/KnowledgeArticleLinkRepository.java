package com.toir.repository;

import com.toir.entity.KnowledgeArticleLink;
import com.toir.enums.KnowledgeTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeArticleLinkRepository extends JpaRepository<KnowledgeArticleLink, UUID> {

    @Query("""
            select l
            from KnowledgeArticleLink l
            where l.isDeleted = false
              and l.knowledgeArticleId = :articleId
            order by l.updatedAt desc
            """)
    List<KnowledgeArticleLink> findAllByKnowledgeArticleIdAndIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("articleId") UUID articleId);

    @Query("""
            select l
            from KnowledgeArticleLink l
            where l.isDeleted = false
              and l.knowledgeArticleId in :articleIds
            order by l.updatedAt desc
            """)
    List<KnowledgeArticleLink> findAllByKnowledgeArticleIdInAndIsDeletedFalse(
            @Param("articleIds") Collection<UUID> articleIds);

    @Query("""
            select l
            from KnowledgeArticleLink l
            where l.isDeleted = false
              and l.targetType = :targetType
              and l.targetId = :targetId
            order by l.updatedAt desc
            """)
    List<KnowledgeArticleLink> findAllByTargetTypeAndTargetIdAndIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("targetType") KnowledgeTargetType targetType,
            @Param("targetId") UUID targetId);

    @Query("""
            select l
            from KnowledgeArticleLink l
            where l.isDeleted = false
              and l.knowledgeArticleId = :articleId
              and l.targetType = :targetType
              and l.targetId = :targetId
            """)
    Optional<KnowledgeArticleLink> findActive(
            @Param("articleId") UUID articleId,
            @Param("targetType") KnowledgeTargetType targetType,
            @Param("targetId") UUID targetId);
}
