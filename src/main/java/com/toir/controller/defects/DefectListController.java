package com.toir.controller.defects;
import com.toir.dto.defectlist.DefectListDto;
import com.toir.dto.defectlist.DefectListLineDto;
import com.toir.dto.defectlist.DefectListRequest;
import com.toir.service.defects.DefectListService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/defect-lists")
@Tag(name = "defect-lists")
public class DefectListController {

    private final DefectListService service;

    public DefectListController(DefectListService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page<DefectListDto>> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.search(equipmentId, page, size, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DefectListDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<DefectListDto> create(@Valid @RequestBody DefectListRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DefectListDto> update(@PathVariable UUID id, @Valid @RequestBody DefectListRequest r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<DefectListDto> approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        return ResponseEntity.ok(service.approve(id, approverId));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<DefectListDto> close(@PathVariable UUID id) { return ResponseEntity.ok(service.close(id)); }

    @PostMapping("/{id}/lines")
    public ResponseEntity<DefectListLineDto> addLine(@PathVariable UUID id, @Valid @RequestBody DefectListLineDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addLine(id, r));
    }

    @DeleteMapping("/lines/{lineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> removeLine(@PathVariable UUID lineId) {
        service.removeLine(lineId);
        return ResponseEntity.noContent().build();
    }
}
