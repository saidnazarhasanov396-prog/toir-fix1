package com.toir.defectlist;

import com.toir.defectlist.dto.DefectListDto;
import com.toir.defectlist.dto.DefectListLineDto;
import com.toir.defectlist.dto.DefectListRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/defect-lists")
@Tag(name = "defect-lists")
public class DefectListController {

    private final DefectListService service;

    public DefectListController(DefectListService service) {
        this.service = service;
    }

    @GetMapping
    public List<DefectListDto> list(@RequestParam(required = false) UUID equipmentId) {
        return equipmentId != null ? service.findByEquipment(equipmentId) : service.findAll();
    }

    @GetMapping("/{id}")
    public DefectListDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<DefectListDto> create(@Valid @RequestBody DefectListRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    public DefectListDto approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        return service.approve(id, approverId);
    }

    @PostMapping("/{id}/close")
    public DefectListDto close(@PathVariable UUID id) { return service.close(id); }

    @PostMapping("/{id}/lines")
    public ResponseEntity<DefectListLineDto> addLine(@PathVariable UUID id, @Valid @RequestBody DefectListLineDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addLine(id, r));
    }

    @DeleteMapping("/lines/{lineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLine(@PathVariable UUID lineId) { service.removeLine(lineId); }
}
