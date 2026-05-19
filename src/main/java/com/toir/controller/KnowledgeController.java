package com.toir.controller;

import com.toir.dto.knowledge.KnowledgeArticleDto;
import com.toir.dto.knowledge.KnowledgeStatsResponse;
import com.toir.entity.KnowledgeArticle;
import com.toir.service.KnowledgeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('KNOWLEDGE_READ')")
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

    @GetMapping("/stats")
    public ResponseEntity<KnowledgeStatsResponse> stats(
            @RequestParam(name = "equipmentId", required = false) UUID equipmentId,
            @RequestParam(name = "equipmentTypeId", required = false) UUID equipmentTypeId,
            @RequestParam(name = "kind", required = false) String kind
    ) {
        return ResponseEntity.ok(service.getStats(equipmentId, equipmentTypeId, kind));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('KNOWLEDGE_READ')")
    public ResponseEntity<KnowledgeArticle> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('KNOWLEDGE_CREATE')")
    public ResponseEntity<KnowledgeArticle> create(@RequestBody KnowledgeArticle article) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(article));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('KNOWLEDGE_UPDATE')")
    public ResponseEntity<KnowledgeArticle> update(@PathVariable UUID id, @RequestBody KnowledgeArticle patch) {
        return ResponseEntity.ok(service.update(id, patch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('KNOWLEDGE_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
