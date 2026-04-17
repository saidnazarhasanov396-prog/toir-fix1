package com.toir.contractorcontract;

import com.toir.contractorcontract.dto.ContractorContractDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contractor-contracts")
@Tag(name = "contractor-contracts")
public class ContractorContractController {

    private final ContractorContractService service;

    public ContractorContractController(ContractorContractService service) { this.service = service; }

    @GetMapping
    public List<ContractorContractDto> list(@RequestParam UUID contractorId) {
        return service.findByContractor(contractorId);
    }

    @PostMapping
    public ResponseEntity<ContractorContractDto> create(@Valid @RequestBody ContractorContractDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ContractorContractDto update(@PathVariable UUID id, @Valid @RequestBody ContractorContractDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
