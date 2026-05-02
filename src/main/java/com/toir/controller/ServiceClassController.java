package com.toir.controller;
import com.toir.dto.serviceclass.ServiceClassDto;
import com.toir.service.ServiceClassService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/service-classes")
@Tag(name = "service-classes")
public class ServiceClassController {

    private final ServiceClassService service;

    public ServiceClassController(ServiceClassService service) { this.service = service; }

    @GetMapping public ResponseEntity<List<ServiceClassDto>> list() { return ResponseEntity.ok(service.findAll()); }
    @GetMapping("/{id}") public ResponseEntity<ServiceClassDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<ServiceClassDto> create(@Valid @RequestBody ServiceClassDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceClassDto> update(@PathVariable UUID id, @Valid @RequestBody ServiceClassDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
