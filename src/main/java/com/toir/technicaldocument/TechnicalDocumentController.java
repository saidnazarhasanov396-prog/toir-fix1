package com.toir.technicaldocument;

import com.toir.technicaldocument.dto.TechnicalDocumentDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "technical-documents")
public class TechnicalDocumentController {

    private final TechnicalDocumentService service;

    public TechnicalDocumentController(TechnicalDocumentService service) { this.service = service; }

    @GetMapping("/equipment/{equipmentId}/documents")
    public List<TechnicalDocumentDto> list(@PathVariable UUID equipmentId) {
        return service.findByEquipment(equipmentId);
    }

    @PostMapping("/equipment/{equipmentId}/documents")
    public ResponseEntity<TechnicalDocumentDto> create(@PathVariable UUID equipmentId, @Valid @RequestBody TechnicalDocumentDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(equipmentId, r));
    }

    @DeleteMapping("/technical-documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
