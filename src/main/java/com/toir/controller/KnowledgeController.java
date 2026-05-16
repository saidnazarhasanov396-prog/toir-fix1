package com.toir.controller;

import com.toir.dto.knowledge.KnowledgeArticleDto;
import com.toir.entity.KnowledgeArticle;
import com.toir.service.KnowledgeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService service;

    @GetMapping
    public ResponseEntity<Page<KnowledgeArticleDto>> list(
            @RequestParam(name = "equipmentId", required = false) UUID equipmentId,
            @RequestParam(name = "equipmentTypeId", required = false) UUID equipmentTypeId,
            @RequestParam(name = "kind", required = false) String kind,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {

        return ResponseEntity.ok(service.list(
                equipmentId,
                equipmentTypeId,
                kind,
                page,
                size
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeArticle> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<KnowledgeArticle> create(@RequestBody KnowledgeArticle article) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(article));
    }

    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeArticle> update(@PathVariable UUID id, @RequestBody KnowledgeArticle patch) {
        return ResponseEntity.ok(service.update(id, patch));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
