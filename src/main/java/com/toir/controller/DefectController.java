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
@RequestMapping("/defects")
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
    public List<DefectDto> list(@RequestParam(required = false) UUID equipmentId) {
        return equipmentId != null ? service.findByEquipment(equipmentId) : service.findAll();
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
     * Создать статью базы знаний (lesson learned) на основании дефекта.
     * Переиспользует description, failureReason и rootCause; оставляет
     * пустыми solution/preventiveActions для заполнения инженером.
     */
    @PostMapping("/{id}/create-lesson")
    @Transactional
    public ResponseEntity<KnowledgeArticle> createLesson(@PathVariable UUID id) {
        Defect d = defectRepository.findById(id)
                .orElseThrow(() -> RestException.notFound("Defect not found: " + id));
        String code = "LL-DEF-" + d.getCode();
        if (knowledgeRepository.existsByCode(code)) {
            throw RestException.conflict("Lesson already exists for defect: " + code);
        }
        KnowledgeArticle a = new KnowledgeArticle();
        a.setCode(code);
        a.setTitle("Дефект " + d.getCode() + ": " + d.getTitle());
        a.setKind("LESSON_LEARNED");
        a.setEquipmentId(d.getEquipmentId());
        a.setDefectId(d.getId());
        a.setWorkOrderId(d.getWorkOrderId());
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
