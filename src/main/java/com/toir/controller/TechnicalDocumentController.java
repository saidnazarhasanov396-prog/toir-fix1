package com.toir.controller;
import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import com.toir.service.TechnicalDocumentService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "technical-documents")
public class TechnicalDocumentController {

    private final TechnicalDocumentService service;

    public TechnicalDocumentController(TechnicalDocumentService service) { this.service = service; }

    @GetMapping("/equipment/{equipmentId}/documents")
    public ResponseEntity<Page<TechnicalDocumentDto>> list(@PathVariable UUID equipmentId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByEquipment(equipmentId), page, size));
    }

    @PostMapping("/equipment/{equipmentId}/documents")
    public ResponseEntity<TechnicalDocumentDto> create(@PathVariable UUID equipmentId, @Valid @RequestBody TechnicalDocumentDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(equipmentId, r));
    }

    @DeleteMapping("/technical-documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
