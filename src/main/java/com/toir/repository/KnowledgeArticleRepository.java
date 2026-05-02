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

    @Query(value = "SELECT * FROM knowledge_articles WHERE equipment_type_id = :equipmentTypeId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<KnowledgeArticle> findAllByEquipmentTypeIdAndIsDeletedFalse(@Param("equipmentTypeId") UUID equipmentTypeId);

    @Query(value = "SELECT * FROM knowledge_articles WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<KnowledgeArticle> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM knowledge_articles WHERE kind = :kind AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<KnowledgeArticle> findAllByKindAndIsDeletedFalse(@Param("kind") String kind);
}
