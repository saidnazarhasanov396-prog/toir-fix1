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
    @Query(value = "SELECT EXISTS(SELECT 1 FROM knowledge_articles WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM knowledge_articles WHERE equipment_type_id = :equipmentTypeId AND is_deleted = false", nativeQuery = true)
    List<KnowledgeArticle> findAllByEquipmentTypeId(@Param("equipmentTypeId") UUID equipmentTypeId);

    @Query(value = "SELECT * FROM knowledge_articles WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<KnowledgeArticle> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM knowledge_articles WHERE kind = :kind AND is_deleted = false", nativeQuery = true)
    List<KnowledgeArticle> findAllByKind(@Param("kind") String kind);
}
