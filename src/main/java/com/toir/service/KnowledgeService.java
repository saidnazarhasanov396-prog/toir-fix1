package com.toir.service;

import com.toir.entity.KnowledgeArticle;
import com.toir.exception.RestException;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeArticleRepository repository;

    @Transactional(readOnly = true)
    public Page<KnowledgeArticle> list(UUID equipmentId, UUID equipmentTypeId, String kind, int page, int size) {
        if (equipmentId != null) {
            return PaginationUtils.page(repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId), page, size);
        }
        if (equipmentTypeId != null) {
            return PaginationUtils.page(repository.findAllByEquipmentTypeIdAndIsDeletedFalse(equipmentTypeId), page, size);
        }
        if (kind != null) {
            return PaginationUtils.page(repository.findAllByKindAndIsDeletedFalse(kind), page, size);
        }
        return PaginationUtils.page(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc(), page, size);
    }

    @Transactional
    public KnowledgeArticle get(UUID id) {
        KnowledgeArticle article = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Article not found: " + id));
        article.setViewCount(article.getViewCount() + 1);
        return article;
    }

    @Transactional
    public KnowledgeArticle create(KnowledgeArticle article) {
        if (article.getCode() == null || article.getCode().isBlank()) {
            throw RestException.badRequest("Code is required");
        }
        if (repository.existsByCodeAndIsDeletedFalse(article.getCode())) {
            throw RestException.conflict("Article code already exists: " + article.getCode());
        }
        if (article.getKind() == null) {
            article.setKind("LESSON_LEARNED");
        }
        return repository.save(article);
    }

    @Transactional
    public KnowledgeArticle update(UUID id, KnowledgeArticle patch) {
        KnowledgeArticle existing = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Article not found: " + id));
        existing.setTitle(patch.getTitle());
        existing.setKind(patch.getKind() != null ? patch.getKind() : existing.getKind());
        existing.setEquipmentTypeId(patch.getEquipmentTypeId());
        existing.setEquipmentId(patch.getEquipmentId());
        existing.setDefectId(patch.getDefectId());
        existing.setWorkOrderId(patch.getWorkOrderId());
        existing.setProblem(patch.getProblem());
        existing.setRootCause(patch.getRootCause());
        existing.setSolution(patch.getSolution());
        existing.setPreventiveActions(patch.getPreventiveActions());
        existing.setTags(patch.getTags());
        return existing;
    }

    @Transactional
    public void delete(UUID id) {
        KnowledgeArticle entity = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Article not found: " + id));
        entity.setDeleted(true);
        repository.save(entity);
    }
}
