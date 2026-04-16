package com.toir.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID> {
    boolean existsByCode(String code);
    List<KnowledgeArticle> findAllByEquipmentTypeId(UUID equipmentTypeId);
    List<KnowledgeArticle> findAllByEquipmentId(UUID equipmentId);
    List<KnowledgeArticle> findAllByKind(String kind);
}
