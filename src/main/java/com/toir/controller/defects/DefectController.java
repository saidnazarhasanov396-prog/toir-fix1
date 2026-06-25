package com.toir.controller.defects;
import com.toir.dto.defect.DefectRequest;
import com.toir.dto.defect.DefectResponse;
import com.toir.dto.defect.DefectStatsResponse;
import com.toir.entity.defects.Defect;
import com.toir.entity.KnowledgeArticle;
import com.toir.enums.DefectStatus;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.service.defects.DefectService;
import com.toir.util.SortUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/defects")
@Tag(name = "defects")
@RequiredArgsConstructor
public class DefectController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "status", "status",
            "severity", "severity",
            "detectedAt", "detectedAt",
            "createdAt", "createdAt"
    );

    private final DefectService service;


    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_READ')")
    public ResponseEntity<Page<DefectResponse>> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID repairRequestId,
            @RequestParam(name = "requestId", required = false) UUID requestIdAlias,
            @RequestParam(required = false) DefectStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir
    ) {
        UUID resolvedRepairRequestId = resolveRepairRequestIdFilter(repairRequestId, requestIdAlias);
        if (sortBy == null || sortBy.isBlank()) {
            return ResponseEntity.ok(service.search(equipmentId, resolvedRepairRequestId, status, category, severity, page, size, search));
        }
        Sort sort = SortUtils.sort(sortBy, sortDir, SORT_FIELDS, "updatedAt", Sort.Direction.DESC);
        return ResponseEntity.ok(service.search(equipmentId, resolvedRepairRequestId, status, page, size, search, sort));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_READ')")
    public ResponseEntity<DefectStatsResponse> stats(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID repairRequestId,
            @RequestParam(name = "requestId", required = false) UUID requestIdAlias,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String search
    ) {
        UUID resolvedRepairRequestId = resolveRepairRequestIdFilter(repairRequestId, requestIdAlias);

        return ResponseEntity.ok(service.getStats(
                equipmentId,
                resolvedRepairRequestId,
                category,
                severity,
                search
        ));
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_READ')")
    public ResponseEntity<DefectResponse> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_CREATE')")
    public ResponseEntity<DefectResponse> create(@Valid @RequestBody DefectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_UPDATE')")
    public ResponseEntity<DefectResponse> update(@PathVariable UUID id, @Valid @RequestBody DefectRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_RESOLVE')")
    public ResponseEntity<DefectResponse> resolve(@PathVariable UUID id) { return ResponseEntity.ok(service.resolve(id)); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_DELETE')")
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
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_UPDATE')")
    public ResponseEntity<KnowledgeArticle> createLesson(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createLesson(id));
    }
}
