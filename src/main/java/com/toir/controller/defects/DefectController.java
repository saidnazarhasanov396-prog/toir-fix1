package com.toir.controller.defects;
import com.toir.dto.defect.DefectRequest;
import com.toir.dto.defect.DefectResponse;
import com.toir.dto.defect.DefectStatsResponse;
import com.toir.entity.defects.Defect;
import com.toir.entity.KnowledgeArticle;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.service.defects.DefectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/defects")
@Tag(name = "defects")
@RequiredArgsConstructor
public class DefectController {

    private final DefectService service;
    private final DefectRepository defectRepository;
    private final KnowledgeArticleRepository knowledgeRepository;


    @GetMapping
    public ResponseEntity<Page<DefectResponse>> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID repairRequestId,
            @RequestParam(name = "requestId", required = false) UUID requestIdAlias,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        UUID resolvedRepairRequestId = resolveRepairRequestIdFilter(repairRequestId, requestIdAlias);
        return ResponseEntity.ok(service.search(equipmentId, resolvedRepairRequestId, page, size, search));
    }

    @GetMapping("/stats")
    public ResponseEntity<DefectStatsResponse> stats(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID repairRequestId,
            @RequestParam(name = "requestId", required = false) UUID requestIdAlias,
            @RequestParam(required = false) String search
    ) {
        UUID resolvedRepairRequestId = resolveRepairRequestIdFilter(repairRequestId, requestIdAlias);

        return ResponseEntity.ok(service.getStats(
                equipmentId,
                resolvedRepairRequestId,
                search
        ));
    }


    @GetMapping("/{id}")
    public ResponseEntity<DefectResponse> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<DefectResponse> create(@Valid @RequestBody DefectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DefectResponse> update(@PathVariable UUID id, @Valid @RequestBody DefectRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<DefectResponse> resolve(@PathVariable UUID id) { return ResponseEntity.ok(service.resolve(id)); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveRepairRequestIdFilter(UUID repairRequestId, UUID requestIdAlias) {
        if (repairRequestId == null) {
            return requestIdAlias;
        }
        if (requestIdAlias == null || repairRequestId.equals(requestIdAlias)) {
            return repairRequestId;
        }
        throw RestException.badRequest("repairRequestId and requestId cannot both be provided with different values");
    }

    /**
     * Создать статью базы знаний (lesson learned) на основании дефекта.
     * Переиспользует description, failureReason и rootCause; оставляет
     * пустыми solution/preventiveActions для заполнения инженером.
     */
    @PostMapping("/{id}/create-lesson")
    @Transactional
    public ResponseEntity<KnowledgeArticle> createLesson(@PathVariable UUID id) {
        Defect d = defectRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Defect not found: " + id));
        String code = "LL-DEF-" + d.getCode();
        if (knowledgeRepository.existsByCodeAndIsDeletedFalse(code)) {
            throw RestException.conflict("Lesson already exists for defect: " + code);
        }
        KnowledgeArticle a = new KnowledgeArticle();
        a.setCode(code);
        a.setTitle("Дефект " + d.getCode() + ": " + d.getTitle());
        a.setKind("LESSON_LEARNED");
        a.setEquipmentId(d.getEquipmentId());
        a.setDefectId(d.getId());
        a.setProblem(d.getDescription() != null ? d.getDescription() : d.getTitle());
        a.setRootCause(
                d.getRootCause() != null
                        ? d.getRootCause()
                        : (d.getFailureReason() != null
                                ? "Причина отказа: " + d.getFailureReason()
                                : "Требуется заполнить по результатам расследования."));
        a.setSolution("Требуется заполнить по результатам расследования.");
        a.setPreventiveActions("Требуется заполнить по результатам расследования.");
        return ResponseEntity.status(HttpStatus.CREATED).body(knowledgeRepository.save(a));
    }
}
