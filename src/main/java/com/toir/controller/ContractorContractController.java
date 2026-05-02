package com.toir.controller;
import com.toir.dto.contractorcontract.ContractorContractDto;
import com.toir.service.ContractorContractService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contractor-contracts")
@Tag(name = "contractor-contracts")
public class ContractorContractController {

    private final ContractorContractService service;

    public ContractorContractController(ContractorContractService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<ContractorContractDto>> list(@RequestParam UUID contractorId) {
        return ResponseEntity.ok(service.findByContractor(contractorId));
    }

    @PostMapping
    public ResponseEntity<ContractorContractDto> create(@Valid @RequestBody ContractorContractDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContractorContractDto> update(@PathVariable UUID id, @Valid @RequestBody ContractorContractDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
