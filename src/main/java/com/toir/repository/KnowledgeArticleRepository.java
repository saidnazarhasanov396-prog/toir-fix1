package com.toir.repository;

import com.toir.entity.KnowledgeArticle;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID> {
    @Query(value = "SELECT * FROM knowledge_articles WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<KnowledgeArticle> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM knowledge_articles WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<KnowledgeArticle> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = """
            SELECT a.*
            FROM knowledge_articles a
            WHERE a.is_deleted = false
              AND (CAST(:q AS varchar) IS NULL OR lower(concat_ws(' ',
                    a.code,
                    a.title,
                    a.problem,
                    a.root_cause,
                    a.solution,
                    coalesce(a.preventive_actions, ''),
                    a.tags::text
              )) LIKE concat('%', lower(CAST(:q AS varchar)), '%'))
              AND (:kindsEmpty = true OR a.kind IN (:kinds))
              AND (CAST(:equipmentId AS varchar) IS NULL
                    OR a.equipment_id = CAST(:equipmentId AS uuid)
                    OR EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false
                          AND l.knowledge_article_id = a.id
                          AND l.target_type = 'EQUIPMENT'
                          AND l.target_id = CAST(:equipmentId AS uuid)
                    ))
              AND (CAST(:equipmentTypeId AS varchar) IS NULL
                    OR a.equipment_type_id = CAST(:equipmentTypeId AS uuid)
                    OR EXISTS (
                        SELECT 1
                        FROM knowledge_article_links l
                        JOIN equipment e ON e.id = l.target_id AND e.is_deleted = false
                        WHERE l.is_deleted = false
                          AND l.knowledge_article_id = a.id
                          AND l.target_type = 'EQUIPMENT'
                          AND e.equipment_type_id = CAST(:equipmentTypeId AS uuid)
                    ))
              AND (CAST(:defectId AS varchar) IS NULL
                    OR a.defect_id = CAST(:defectId AS uuid)
                    OR EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false
                          AND l.knowledge_article_id = a.id
                          AND l.target_type = 'DEFECT'
                          AND l.target_id = CAST(:defectId AS uuid)
                    ))
              AND (CAST(:workOrderId AS varchar) IS NULL
                    OR a.work_order_id = CAST(:workOrderId AS uuid)
                    OR EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false
                          AND l.knowledge_article_id = a.id
                          AND l.target_type = 'WORK_ORDER'
                          AND l.target_id = CAST(:workOrderId AS uuid)
                    ))
              AND (CAST(:targetType AS varchar) IS NULL OR CAST(:targetId AS varchar) IS NULL
                    OR EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false
                          AND l.knowledge_article_id = a.id
                          AND l.target_type = CAST(:targetType AS varchar)
                          AND l.target_id = CAST(:targetId AS uuid)
                    ))
              AND (:tagsEmpty = true OR EXISTS (
                    SELECT 1
                    FROM jsonb_array_elements_text(coalesce(a.tags, '[]'::jsonb)) tag(value)
                    WHERE lower(tag.value) IN (:tags)
              ))
              AND (CAST(:createdFrom AS varchar) IS NULL OR a.created_at >= CAST(:createdFrom AS timestamptz))
              AND (CAST(:createdTo AS varchar) IS NULL OR a.created_at <= CAST(:createdTo AS timestamptz))
              AND (CAST(:updatedFrom AS varchar) IS NULL OR a.updated_at >= CAST(:updatedFrom AS timestamptz))
              AND (CAST(:updatedTo AS varchar) IS NULL OR a.updated_at <= CAST(:updatedTo AS timestamptz))
              AND (CAST(:hasLinks AS varchar) IS NULL
                    OR (CAST(:hasLinks AS boolean) = true AND EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false AND l.knowledge_article_id = a.id
                    ))
                    OR (CAST(:hasLinks AS boolean) = false AND NOT EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false AND l.knowledge_article_id = a.id
                    )))
            ORDER BY
              CASE WHEN :sort = 'title,asc' THEN lower(a.title) END ASC,
              CASE WHEN :sort = 'code,asc' THEN lower(a.code) END ASC,
              CASE WHEN :sort = 'kind,asc' THEN lower(a.kind) END ASC,
              CASE WHEN :sort = 'createdAt,desc' THEN a.created_at END DESC,
              CASE WHEN :sort = 'viewCount,desc' THEN a.view_count END DESC,
              a.updated_at DESC
            """, countQuery = """
            SELECT count(a.id)
            FROM knowledge_articles a
            WHERE a.is_deleted = false
              AND (CAST(:q AS varchar) IS NULL OR lower(concat_ws(' ',
                    a.code,
                    a.title,
                    a.problem,
                    a.root_cause,
                    a.solution,
                    coalesce(a.preventive_actions, ''),
                    a.tags::text
              )) LIKE concat('%', lower(CAST(:q AS varchar)), '%'))
              AND (:kindsEmpty = true OR a.kind IN (:kinds))
              AND (CAST(:equipmentId AS varchar) IS NULL
                    OR a.equipment_id = CAST(:equipmentId AS uuid)
                    OR EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false
                          AND l.knowledge_article_id = a.id
                          AND l.target_type = 'EQUIPMENT'
                          AND l.target_id = CAST(:equipmentId AS uuid)
                    ))
              AND (CAST(:equipmentTypeId AS varchar) IS NULL
                    OR a.equipment_type_id = CAST(:equipmentTypeId AS uuid)
                    OR EXISTS (
                        SELECT 1
                        FROM knowledge_article_links l
                        JOIN equipment e ON e.id = l.target_id AND e.is_deleted = false
                        WHERE l.is_deleted = false
                          AND l.knowledge_article_id = a.id
                          AND l.target_type = 'EQUIPMENT'
                          AND e.equipment_type_id = CAST(:equipmentTypeId AS uuid)
                    ))
              AND (CAST(:defectId AS varchar) IS NULL
                    OR a.defect_id = CAST(:defectId AS uuid)
                    OR EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false
                          AND l.knowledge_article_id = a.id
                          AND l.target_type = 'DEFECT'
                          AND l.target_id = CAST(:defectId AS uuid)
                    ))
              AND (CAST(:workOrderId AS varchar) IS NULL
                    OR a.work_order_id = CAST(:workOrderId AS uuid)
                    OR EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false
                          AND l.knowledge_article_id = a.id
                          AND l.target_type = 'WORK_ORDER'
                          AND l.target_id = CAST(:workOrderId AS uuid)
                    ))
              AND (CAST(:targetType AS varchar) IS NULL OR CAST(:targetId AS varchar) IS NULL
                    OR EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false
                          AND l.knowledge_article_id = a.id
                          AND l.target_type = CAST(:targetType AS varchar)
                          AND l.target_id = CAST(:targetId AS uuid)
                    ))
              AND (:tagsEmpty = true OR EXISTS (
                    SELECT 1
                    FROM jsonb_array_elements_text(coalesce(a.tags, '[]'::jsonb)) tag(value)
                    WHERE lower(tag.value) IN (:tags)
              ))
              AND (CAST(:createdFrom AS varchar) IS NULL OR a.created_at >= CAST(:createdFrom AS timestamptz))
              AND (CAST(:createdTo AS varchar) IS NULL OR a.created_at <= CAST(:createdTo AS timestamptz))
              AND (CAST(:updatedFrom AS varchar) IS NULL OR a.updated_at >= CAST(:updatedFrom AS timestamptz))
              AND (CAST(:updatedTo AS varchar) IS NULL OR a.updated_at <= CAST(:updatedTo AS timestamptz))
              AND (CAST(:hasLinks AS varchar) IS NULL
                    OR (CAST(:hasLinks AS boolean) = true AND EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false AND l.knowledge_article_id = a.id
                    ))
                    OR (CAST(:hasLinks AS boolean) = false AND NOT EXISTS (
                        SELECT 1 FROM knowledge_article_links l
                        WHERE l.is_deleted = false AND l.knowledge_article_id = a.id
                    )))
            """, nativeQuery = true)
    Page<KnowledgeArticle> search(
            @Param("q") String q,
            @Param("kindsEmpty") boolean kindsEmpty,
            @Param("kinds") Collection<String> kinds,
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId,
            @Param("equipmentId") UUID equipmentId,
            @Param("equipmentTypeId") UUID equipmentTypeId,
            @Param("defectId") UUID defectId,
            @Param("workOrderId") UUID workOrderId,
            @Param("tagsEmpty") boolean tagsEmpty,
            @Param("tags") Collection<String> tags,
            @Param("createdFrom") java.time.Instant createdFrom,
            @Param("createdTo") java.time.Instant createdTo,
            @Param("updatedFrom") java.time.Instant updatedFrom,
            @Param("updatedTo") java.time.Instant updatedTo,
            @Param("hasLinks") Boolean hasLinks,
            @Param("sort") String sort,
            Pageable pageable
    );

    @Query(value = "SELECT * FROM knowledge_articles WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<KnowledgeArticle> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM knowledge_articles WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM knowledge_articles WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM knowledge_articles WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM knowledge_articles
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT * FROM knowledge_articles WHERE equipment_type_id = :equipmentTypeId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<KnowledgeArticle> findAllByEquipmentTypeIdAndIsDeletedFalse(@Param("equipmentTypeId") UUID equipmentTypeId);

    @Query(value = "SELECT * FROM knowledge_articles WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<KnowledgeArticle> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM knowledge_articles WHERE kind = :kind AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<KnowledgeArticle> findAllByKindAndIsDeletedFalse(@Param("kind") String kind);

    @Query("""
        select distinct a.defectId
        from KnowledgeArticle a
        where a.isDeleted = false
          and a.defectId in :defectIds
          and a.kind = :kind
        """)
    List<UUID> findDefectIdsWithLesson(
            @Param("defectIds") Collection<UUID> defectIds,
            @Param("kind") String kind
    );

    @Query(nativeQuery = true, value = """
        select
            count(a.id) as totalArticles,
            count(a.id) filter (where a.kind = 'LESSON_LEARNED') as lessonLearned,
            count(a.id) filter (where a.kind = 'PROCEDURE') as procedures,
            count(a.id) filter (where a.kind = 'TROUBLESHOOTING') as troubleshooting
        from knowledge_articles a
        where a.is_deleted = false
          and (cast(:equipmentId as varchar) is null or a.equipment_id = cast(:equipmentId as uuid))
          and (cast(:equipmentTypeId as varchar) is null or a.equipment_type_id = cast(:equipmentTypeId as uuid))
          and (cast(:kind as varchar) is null or a.kind = cast(:kind as varchar))
    """)
    KnowledgeStatsProjection getKnowledgeStats(
            @Param("equipmentId") UUID equipmentId,
            @Param("equipmentTypeId") UUID equipmentTypeId,
            @Param("kind") String kind
    );


    @Query(nativeQuery = true, value = """
        select
            count(a.id) as totalArticles,
            count(a.id) filter (where a.kind = 'LESSON_LEARNED') as lessonLearned,
            count(a.id) filter (where a.kind = 'PROCEDURE') as procedures,
            count(a.id) filter (where a.kind = 'TROUBLESHOOTING') as troubleshooting
        from knowledge_articles a
        where a.is_deleted = false
          and (CAST(:q AS varchar) IS NULL OR lower(concat_ws(' ',
                a.code,
                a.title,
                a.problem,
                a.root_cause,
                a.solution,
                coalesce(a.preventive_actions, ''),
                a.tags::text
          )) LIKE concat('%', lower(CAST(:q AS varchar)), '%'))
          and (:kindsEmpty = true OR a.kind IN (:kinds))
          and (CAST(:equipmentId AS varchar) IS NULL
                OR a.equipment_id = CAST(:equipmentId AS uuid)
                OR EXISTS (
                    SELECT 1 FROM knowledge_article_links l
                    WHERE l.is_deleted = false
                      AND l.knowledge_article_id = a.id
                      AND l.target_type = 'EQUIPMENT'
                      AND l.target_id = CAST(:equipmentId AS uuid)
                ))
          and (CAST(:equipmentTypeId AS varchar) IS NULL
                OR a.equipment_type_id = CAST(:equipmentTypeId AS uuid)
                OR EXISTS (
                    SELECT 1
                    FROM knowledge_article_links l
                    JOIN equipment e ON e.id = l.target_id AND e.is_deleted = false
                    WHERE l.is_deleted = false
                      AND l.knowledge_article_id = a.id
                      AND l.target_type = 'EQUIPMENT'
                      AND e.equipment_type_id = CAST(:equipmentTypeId AS uuid)
                ))
          and (CAST(:defectId AS varchar) IS NULL
                OR a.defect_id = CAST(:defectId AS uuid)
                OR EXISTS (
                    SELECT 1 FROM knowledge_article_links l
                    WHERE l.is_deleted = false
                      AND l.knowledge_article_id = a.id
                      AND l.target_type = 'DEFECT'
                      AND l.target_id = CAST(:defectId AS uuid)
                ))
          and (CAST(:workOrderId AS varchar) IS NULL
                OR a.work_order_id = CAST(:workOrderId AS uuid)
                OR EXISTS (
                    SELECT 1 FROM knowledge_article_links l
                    WHERE l.is_deleted = false
                      AND l.knowledge_article_id = a.id
                      AND l.target_type = 'WORK_ORDER'
                      AND l.target_id = CAST(:workOrderId AS uuid)
                ))
          and (CAST(:targetType AS varchar) IS NULL OR CAST(:targetId AS varchar) IS NULL
                OR EXISTS (
                    SELECT 1 FROM knowledge_article_links l
                    WHERE l.is_deleted = false
                      AND l.knowledge_article_id = a.id
                      AND l.target_type = CAST(:targetType AS varchar)
                      AND l.target_id = CAST(:targetId AS uuid)
                ))
          and (:tagsEmpty = true OR EXISTS (
                SELECT 1
                FROM jsonb_array_elements_text(coalesce(a.tags, '[]'::jsonb)) tag(value)
                WHERE lower(tag.value) IN (:tags)
          ))
          and (CAST(:createdFrom AS varchar) IS NULL OR a.created_at >= CAST(:createdFrom AS timestamptz))
          and (CAST(:createdTo AS varchar) IS NULL OR a.created_at <= CAST(:createdTo AS timestamptz))
          and (CAST(:updatedFrom AS varchar) IS NULL OR a.updated_at >= CAST(:updatedFrom AS timestamptz))
          and (CAST(:updatedTo AS varchar) IS NULL OR a.updated_at <= CAST(:updatedTo AS timestamptz))
          and (CAST(:hasLinks AS varchar) IS NULL
                OR (CAST(:hasLinks AS boolean) = true AND EXISTS (
                    SELECT 1 FROM knowledge_article_links l
                    WHERE l.is_deleted = false AND l.knowledge_article_id = a.id
                ))
                OR (CAST(:hasLinks AS boolean) = false AND NOT EXISTS (
                    SELECT 1 FROM knowledge_article_links l
                    WHERE l.is_deleted = false AND l.knowledge_article_id = a.id
                )))
    """)
    KnowledgeStatsProjection getKnowledgeStatsSearch(
            @Param("q") String q,
            @Param("kindsEmpty") boolean kindsEmpty,
            @Param("kinds") Collection<String> kinds,
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId,
            @Param("equipmentId") UUID equipmentId,
            @Param("equipmentTypeId") UUID equipmentTypeId,
            @Param("defectId") UUID defectId,
            @Param("workOrderId") UUID workOrderId,
            @Param("tagsEmpty") boolean tagsEmpty,
            @Param("tags") Collection<String> tags,
            @Param("createdFrom") java.time.Instant createdFrom,
            @Param("createdTo") java.time.Instant createdTo,
            @Param("updatedFrom") java.time.Instant updatedFrom,
            @Param("updatedTo") java.time.Instant updatedTo,
            @Param("hasLinks") Boolean hasLinks
    );
}
