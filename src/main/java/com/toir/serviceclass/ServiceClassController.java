package com.toir.serviceclass;

import com.toir.serviceclass.dto.ServiceClassDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/service-classes")
@Tag(name = "service-classes")
public class ServiceClassController {

    private final ServiceClassService service;

    public ServiceClassController(ServiceClassService service) { this.service = service; }

    @GetMapping public List<ServiceClassDto> list() { return service.findAll(); }
    @GetMapping("/{id}") public ServiceClassDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<ServiceClassDto> create(@Valid @RequestBody ServiceClassDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ServiceClassDto update(@PathVariable UUID id, @Valid @RequestBody ServiceClassDto r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
