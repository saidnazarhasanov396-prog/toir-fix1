package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.KnowledgeArticle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID> {
    java.util.Optional<KnowledgeArticle> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<KnowledgeArticle> findAllByIsDeletedFalse();

    java.util.List<KnowledgeArticle> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM knowledge_articles WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM knowledge_articles WHERE equipment_type_id = :equipmentTypeId AND is_deleted = false", nativeQuery = true)
    List<KnowledgeArticle> findAllByEquipmentTypeIdAndIsDeletedFalse(@Param("equipmentTypeId") UUID equipmentTypeId);

    @Query(value = "SELECT * FROM knowledge_articles WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<KnowledgeArticle> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM knowledge_articles WHERE kind = :kind AND is_deleted = false", nativeQuery = true)
    List<KnowledgeArticle> findAllByKindAndIsDeletedFalse(@Param("kind") String kind);
}
