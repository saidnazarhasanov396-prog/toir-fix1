package com.toir.controller.defects;
import com.toir.dto.defectlist.DefectListDto;
import com.toir.dto.defectlist.DefectListLineDto;
import com.toir.dto.defectlist.DefectListRequest;
import com.toir.dto.defectlist.DefectListStatsResponse;
import com.toir.service.defects.DefectListService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/defect-lists")
@Tag(name = "defect-lists")
@RequiredArgsConstructor
public class DefectListController {

    private final DefectListService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_LIST_READ')")
    public ResponseEntity<Page<DefectListDto>> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.search(equipmentId, page, size, search));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_LIST_READ')")
    public ResponseEntity<DefectListStatsResponse> stats(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.getStats(equipmentId, search));
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_LIST_READ')")
    public ResponseEntity<DefectListDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_LIST_CREATE')")
    public ResponseEntity<DefectListDto> create(@Valid @RequestBody DefectListRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_LIST_UPDATE')")
    public ResponseEntity<DefectListDto> update(@PathVariable UUID id, @Valid @RequestBody DefectListRequest r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_LIST_APPROVE')")
    public ResponseEntity<DefectListDto> approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        return ResponseEntity.ok(service.approve(id, approverId));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_LIST_CLOSE')")
    public ResponseEntity<DefectListDto> close(@PathVariable UUID id) { return ResponseEntity.ok(service.close(id)); }

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_LIST_UPDATE')")
    public ResponseEntity<DefectListLineDto> addLine(@PathVariable UUID id, @Valid @RequestBody DefectListLineDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addLine(id, r));
    }

    @DeleteMapping("/lines/{lineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEFECT_LIST_DELETE')")
    public ResponseEntity<Void> removeLine(@PathVariable UUID lineId) {
        service.removeLine(lineId);
        return ResponseEntity.noContent().build();
    }
}
