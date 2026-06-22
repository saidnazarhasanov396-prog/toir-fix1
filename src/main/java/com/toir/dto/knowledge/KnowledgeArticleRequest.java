package com.toir.dto.knowledge;

import com.toir.entity.KnowledgeArticle;

import java.util.List;
import java.util.UUID;

public record KnowledgeArticleRequest(
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
        List<KnowledgeArticleLinkDto> links
) {
    public KnowledgeArticle toArticle() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setCode(code);
        article.setTitle(title);
        article.setKind(kind);
        article.setEquipmentTypeId(equipmentTypeId);
        article.setEquipmentId(equipmentId);
        article.setDefectId(defectId);
        article.setWorkOrderId(workOrderId);
        article.setProblem(problem);
        article.setRootCause(rootCause);
        article.setSolution(solution);
        article.setPreventiveActions(preventiveActions);
        article.setTags(tags);
        article.setAuthorId(authorId);
        article.setLinks(links);
        return article;
    }
}
