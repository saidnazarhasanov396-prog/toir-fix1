package com.toir.controller;
import com.toir.service.ContractorService;

import com.toir.dto.contractor.ContractorDto;
import com.toir.dto.contractor.ContractorRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/contractors")
@Tag(name = "contractors")
public class ContractorController {

    private final ContractorService service;

    public ContractorController(ContractorService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContractorDto> list() { return service.findAll(); }

    @GetMapping("/{id}")
    public ContractorDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<ContractorDto> create(@Valid @RequestBody ContractorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ContractorDto update(@PathVariable UUID id, @Valid @RequestBody ContractorRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
