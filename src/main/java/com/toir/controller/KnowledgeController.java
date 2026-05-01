package com.toir.controller;
import com.toir.entity.KnowledgeArticle;
import com.toir.repository.KnowledgeArticleRepository;

import com.toir.exception.RestException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CRUD + Ð¿Ð¾Ð¸ÑÐº ÑÑ‚Ð°Ñ‚ÐµÐ¹ Ð±Ð°Ð·Ñ‹ Ð·Ð½Ð°Ð½Ð¸Ð¹. Ð£Ð²ÐµÐ»Ð¸Ñ‡Ð¸Ð²Ð°ÐµÑ‚ ÑÑ‡Ñ‘Ñ‚Ñ‡Ð¸Ðº Ð¿Ñ€Ð¾ÑÐ¼Ð¾Ñ‚Ñ€Ð¾Ð² Ð¿Ñ€Ð¸
 * Ñ‡Ñ‚ÐµÐ½Ð¸Ð¸ ÐºÐ¾Ð½ÐºÑ€ÐµÑ‚Ð½Ð¾Ð¹ ÑÑ‚Ð°Ñ‚ÑŒÐ¸.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "knowledge")
@Transactional
public class KnowledgeController {

    private final KnowledgeArticleRepository repo;

    public KnowledgeController(KnowledgeArticleRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<KnowledgeArticle> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) String kind
    ) {
        if (equipmentId != null) return com.toir.util.UpdatedAtSorter.descending(repo.findAllByEquipmentIdAndIsDeletedFalse(equipmentId));
        if (equipmentTypeId != null) return com.toir.util.UpdatedAtSorter.descending(repo.findAllByEquipmentTypeIdAndIsDeletedFalse(equipmentTypeId));
        if (kind != null) return com.toir.util.UpdatedAtSorter.descending(repo.findAllByKindAndIsDeletedFalse(kind));
        return com.toir.util.UpdatedAtSorter.descending(repo.findAllByIsDeletedFalse());
    }

    @GetMapping("/{id}")
    public KnowledgeArticle get(@PathVariable UUID id) {
        KnowledgeArticle a = repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Article not found: " + id));
        a.setViewCount(a.getViewCount() + 1);
        return a;
    }

    @PostMapping
    public ResponseEntity<KnowledgeArticle> create(@RequestBody KnowledgeArticle article) {
        if (article.getCode() == null || article.getCode().isBlank()) {
            throw RestException.badRequest("Code is required");
        }
        if (repo.existsByCodeAndIsDeletedFalse(article.getCode())) {
            throw RestException.conflict("Article code already exists: " + article.getCode());
        }
        if (article.getKind() == null) article.setKind("LESSON_LEARNED");
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(article));
    }

    @PutMapping("/{id}")
    public KnowledgeArticle update(@PathVariable UUID id, @RequestBody KnowledgeArticle patch) {
        KnowledgeArticle existing = repo.findByIdAndIsDeletedFalse(id)
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        var entity = repo.findByIdAndIsDeletedFalse(id).orElseThrow();
        entity.setDeleted(true);
        repo.save(entity);
        return ResponseEntity.noContent().build();
    }
}
