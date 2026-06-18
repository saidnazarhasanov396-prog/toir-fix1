package com.toir.repository;

import com.toir.entity.KnowledgeArticle;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
}
