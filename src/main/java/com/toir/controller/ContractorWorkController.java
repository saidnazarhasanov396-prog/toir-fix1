package com.toir.controller;
import com.toir.dto.contractorwork.ContractorWorkDto;
import com.toir.service.ContractorWorkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contractor-works")
@Tag(name = "contractor-works")
public class ContractorWorkController {

    private final ContractorWorkService service;

    public ContractorWorkController(ContractorWorkService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<ContractorWorkDto>> list(@RequestParam UUID contractorId) {
        return ResponseEntity.ok(service.findByContractor(contractorId));
    }

    @PostMapping
    public ResponseEntity<ContractorWorkDto> create(@Valid @RequestBody ContractorWorkDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ContractorWorkDto> start(@PathVariable UUID id) { return ResponseEntity.ok(service.start(id)); }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ContractorWorkDto> complete(@PathVariable UUID id, @RequestParam String result, @RequestParam(required = false) Double cost) {
        return ResponseEntity.ok(service.complete(id, result, cost));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ContractorWorkDto> accept(@PathVariable UUID id, @RequestParam UUID acceptedById, @RequestParam(required = false) String comment) {
        return ResponseEntity.ok(service.accept(id, acceptedById, comment));
    }
}
