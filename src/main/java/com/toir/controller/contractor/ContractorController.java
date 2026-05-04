package com.toir.controller.contractor;
import com.toir.dto.contractor.ContractorDto;
import com.toir.dto.contractor.ContractorRequest;
import com.toir.service.contactor.ContractorService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contractors")
@Tag(name = "contractors")
@RequiredArgsConstructor
public class ContractorController {

    private final ContractorService service;

    @GetMapping
    public ResponseEntity<Page<ContractorDto>> list(@RequestParam(required = false) String search,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(search), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContractorDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<ContractorDto> create(@Valid @RequestBody ContractorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContractorDto> update(@PathVariable UUID id, @Valid @RequestBody ContractorRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
