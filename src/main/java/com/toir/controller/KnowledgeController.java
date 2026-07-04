package com.toir.controller;

import com.toir.dto.knowledge.KnowledgeArticleDto;
import com.toir.dto.knowledge.KnowledgeArticleLinkDto;
import com.toir.dto.knowledge.KnowledgeArticleSearchRequest;
import com.toir.dto.knowledge.KnowledgeContextResponse;
import com.toir.dto.knowledge.KnowledgeStatsResponse;
import com.toir.entity.KnowledgeArticle;
import com.toir.enums.KnowledgeTargetType;
import com.toir.service.KnowledgeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
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
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "equipmentId", required = false) UUID equipmentId,
            @RequestParam(name = "equipmentTypeId", required = false) UUID equipmentTypeId,
            @RequestParam(name = "kind", required = false) List<String> kinds,
            @RequestParam(name = "targetType", required = false) KnowledgeTargetType targetType,
            @RequestParam(name = "targetId", required = false) UUID targetId,
            @RequestParam(name = "defectId", required = false) UUID defectId,
            @RequestParam(name = "workOrderId", required = false) UUID workOrderId,
            @RequestParam(name = "tag", required = false) List<String> tags,
            @RequestParam(name = "createdFrom", required = false) Instant createdFrom,
            @RequestParam(name = "createdTo", required = false) Instant createdTo,
            @RequestParam(name = "updatedFrom", required = false) Instant updatedFrom,
            @RequestParam(name = "updatedTo", required = false) Instant updatedTo,
            @RequestParam(name = "hasLinks", required = false) Boolean hasLinks,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        if (!hasAdvancedFilters(q, kinds, targetType, targetId, defectId, workOrderId, tags,
                createdFrom, createdTo, updatedFrom, updatedTo, hasLinks, sort)) {
            String legacyKind = kinds == null || kinds.isEmpty() ? null : kinds.getFirst();
            return ResponseEntity.ok(service.list(equipmentId, equipmentTypeId, legacyKind, page, size));
        }

        return ResponseEntity.ok(service.search(new KnowledgeArticleSearchRequest(
                q,
                kinds,
                targetType,
                targetId,
                equipmentId,
                equipmentTypeId,
                defectId,
                workOrderId,
                tags,
                createdFrom,
                createdTo,
                updatedFrom,
                updatedTo,
                hasLinks,
                page,
                size,
                sort
        )));
    }

    private boolean hasAdvancedFilters(String q,
                                       List<String> kinds,
                                       KnowledgeTargetType targetType,
                                       UUID targetId,
                                       UUID defectId,
                                       UUID workOrderId,
                                       List<String> tags,
                                       Instant createdFrom,
                                       Instant createdTo,
                                       Instant updatedFrom,
                                       Instant updatedTo,
                                       Boolean hasLinks,
                                       String sort) {
        return (q != null && !q.isBlank())
                || (kinds != null && kinds.size() > 1)
                || targetType != null
                || targetId != null
                || defectId != null
                || workOrderId != null
                || (tags != null && !tags.isEmpty())
                || createdFrom != null
                || createdTo != null
                || updatedFrom != null
                || updatedTo != null
                || hasLinks != null
                || (sort != null && !sort.isBlank());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('KNOWLEDGE_READ')")
    public ResponseEntity<KnowledgeStatsResponse> stats(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "equipmentId", required = false) UUID equipmentId,
            @RequestParam(name = "equipmentTypeId", required = false) UUID equipmentTypeId,
            @RequestParam(name = "kind", required = false) List<String> kinds,
            @RequestParam(name = "targetType", required = false) KnowledgeTargetType targetType,
            @RequestParam(name = "targetId", required = false) UUID targetId,
            @RequestParam(name = "defectId", required = false) UUID defectId,
            @RequestParam(name = "workOrderId", required = false) UUID workOrderId,
            @RequestParam(name = "tag", required = false) List<String> tags,
            @RequestParam(name = "createdFrom", required = false) Instant createdFrom,
            @RequestParam(name = "createdTo", required = false) Instant createdTo,
            @RequestParam(name = "updatedFrom", required = false) Instant updatedFrom,
            @RequestParam(name = "updatedTo", required = false) Instant updatedTo,
            @RequestParam(name = "hasLinks", required = false) Boolean hasLinks
    ) {
        if (!hasAdvancedFilters(q, kinds, targetType, targetId, defectId, workOrderId, tags,
                createdFrom, createdTo, updatedFrom, updatedTo, hasLinks, null)) {
            String legacyKind = kinds == null || kinds.isEmpty() ? null : kinds.getFirst();
            return ResponseEntity.ok(service.getStats(equipmentId, equipmentTypeId, legacyKind));
        }

        return ResponseEntity.ok(service.getStats(new KnowledgeArticleSearchRequest(
                q,
                kinds,
                targetType,
                targetId,
                equipmentId,
                equipmentTypeId,
                defectId,
                workOrderId,
                tags,
                createdFrom,
                createdTo,
                updatedFrom,
                updatedTo,
                hasLinks,
                0,
                1,
                null
        )));
    }

    @GetMapping("/context")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('KNOWLEDGE_READ')")
    public ResponseEntity<KnowledgeContextResponse> context(
            @RequestParam KnowledgeTargetType targetType,
            @RequestParam UUID targetId,
            @RequestParam(name = "size", defaultValue = "5") int size
    ) {
        return ResponseEntity.ok(service.context(targetType, targetId, size));
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

    @PostMapping("/{id}/links")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('KNOWLEDGE_UPDATE')")
    public ResponseEntity<KnowledgeArticleDto> link(@PathVariable UUID id, @RequestBody KnowledgeArticleLinkDto link) {
        return ResponseEntity.ok(service.link(id, link));
    }

    @DeleteMapping("/{id}/links")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('KNOWLEDGE_UPDATE')")
    public ResponseEntity<Void> unlink(
            @PathVariable UUID id,
            @RequestParam KnowledgeTargetType targetType,
            @RequestParam UUID targetId
    ) {
        service.unlink(id, targetType, targetId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('KNOWLEDGE_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
