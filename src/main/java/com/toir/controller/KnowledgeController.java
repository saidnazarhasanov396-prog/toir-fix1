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
 * CRUD + поиск статей базы знаний. Увеличивает счётчик просмотров при
 * чтении конкретной статьи.
 */
@RestController
@RequestMapping("/knowledge")
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
        if (equipmentId != null) return repo.findAllByEquipmentId(equipmentId);
        if (equipmentTypeId != null) return repo.findAllByEquipmentTypeId(equipmentTypeId);
        if (kind != null) return repo.findAllByKind(kind);
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public KnowledgeArticle get(@PathVariable UUID id) {
        KnowledgeArticle a = repo.findById(id)
                .orElseThrow(() -> RestException.notFound("Article not found: " + id));
        a.setViewCount(a.getViewCount() + 1);
        return a;
    }

    @PostMapping
    public ResponseEntity<KnowledgeArticle> create(@RequestBody KnowledgeArticle article) {
        if (article.getCode() == null || article.getCode().isBlank()) {
            throw RestException.badRequest("Code is required");
        }
        if (repo.existsByCode(article.getCode())) {
            throw RestException.conflict("Article code already exists: " + article.getCode());
        }
        if (article.getKind() == null) article.setKind("LESSON_LEARNED");
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(article));
    }

    @PutMapping("/{id}")
    public KnowledgeArticle update(@PathVariable UUID id, @RequestBody KnowledgeArticle patch) {
        KnowledgeArticle existing = repo.findById(id)
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
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
