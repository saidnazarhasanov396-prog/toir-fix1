package com.toir.controller;
import com.toir.entity.Defect;
import com.toir.repository.DefectRepository;
import com.toir.service.DefectService;

import com.toir.exception.RestException;
import com.toir.dto.defect.DefectDto;
import com.toir.dto.defect.DefectRequest;
import com.toir.entity.KnowledgeArticle;
import com.toir.repository.KnowledgeArticleRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/defects")
@Tag(name = "defects")
public class DefectController {

    private final DefectService service;
    private final DefectRepository defectRepository;
    private final KnowledgeArticleRepository knowledgeRepository;

    public DefectController(DefectService service,
                            DefectRepository defectRepository,
                            KnowledgeArticleRepository knowledgeRepository) {
        this.service = service;
        this.defectRepository = defectRepository;
        this.knowledgeRepository = knowledgeRepository;
    }

    @GetMapping
    public List<DefectDto> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return service.search(equipmentId, page, size, search);
    }

    @GetMapping("/{id}")
    public DefectDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<DefectDto> create(@Valid @RequestBody DefectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public DefectDto update(@PathVariable UUID id, @Valid @RequestBody DefectRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/resolve")
    public DefectDto resolve(@PathVariable UUID id) { return service.resolve(id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }

    /**
     * Ð¡Ð¾Ð·Ð´Ð°Ñ‚ÑŒ ÑÑ‚Ð°Ñ‚ÑŒÑŽ Ð±Ð°Ð·Ñ‹ Ð·Ð½Ð°Ð½Ð¸Ð¹ (lesson learned) Ð½Ð° Ð¾ÑÐ½Ð¾Ð²Ð°Ð½Ð¸Ð¸ Ð´ÐµÑ„ÐµÐºÑ‚Ð°.
     * ÐŸÐµÑ€ÐµÐ¸ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÑ‚ description, failureReason Ð¸ rootCause; Ð¾ÑÑ‚Ð°Ð²Ð»ÑÐµÑ‚
     * Ð¿ÑƒÑÑ‚Ñ‹Ð¼Ð¸ solution/preventiveActions Ð´Ð»Ñ Ð·Ð°Ð¿Ð¾Ð»Ð½ÐµÐ½Ð¸Ñ Ð¸Ð½Ð¶ÐµÐ½ÐµÑ€Ð¾Ð¼.
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
        a.setTitle("Ð”ÐµÑ„ÐµÐºÑ‚ " + d.getCode() + ": " + d.getTitle());
        a.setKind("LESSON_LEARNED");
        a.setEquipmentId(d.getEquipmentId());
        a.setDefectId(d.getId());
        a.setWorkOrderId(d.getWorkOrderId());
        a.setProblem(d.getDescription() != null ? d.getDescription() : d.getTitle());
        a.setRootCause(
                d.getRootCause() != null
                        ? d.getRootCause()
                        : (d.getFailureReason() != null
                                ? "ÐŸÑ€Ð¸Ñ‡Ð¸Ð½Ð° Ð¾Ñ‚ÐºÐ°Ð·Ð°: " + d.getFailureReason()
                                : "Ð¢Ñ€ÐµÐ±ÑƒÐµÑ‚ÑÑ Ð·Ð°Ð¿Ð¾Ð»Ð½Ð¸Ñ‚ÑŒ Ð¿Ð¾ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚Ð°Ð¼ Ñ€Ð°ÑÑÐ»ÐµÐ´Ð¾Ð²Ð°Ð½Ð¸Ñ."));
        a.setSolution("Ð¢Ñ€ÐµÐ±ÑƒÐµÑ‚ÑÑ Ð·Ð°Ð¿Ð¾Ð»Ð½Ð¸Ñ‚ÑŒ Ð¿Ð¾ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚Ð°Ð¼ Ñ€Ð°ÑÑÐ»ÐµÐ´Ð¾Ð²Ð°Ð½Ð¸Ñ.");
        a.setPreventiveActions("Ð¢Ñ€ÐµÐ±ÑƒÐµÑ‚ÑÑ Ð·Ð°Ð¿Ð¾Ð»Ð½Ð¸Ñ‚ÑŒ Ð¿Ð¾ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚Ð°Ð¼ Ñ€Ð°ÑÑÐ»ÐµÐ´Ð¾Ð²Ð°Ð½Ð¸Ñ.");
        return ResponseEntity.status(HttpStatus.CREATED).body(knowledgeRepository.save(a));
    }
}
