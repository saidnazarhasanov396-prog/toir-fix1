package com.toir.location;

import com.toir.location.dto.LocationDto;
import com.toir.location.dto.LocationRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "locations")
public class LocationController {

    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    @GetMapping
    public List<LocationDto> list() { return service.findAll(); }

    @GetMapping("/{id}")
    public LocationDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<LocationDto> create(@Valid @RequestBody LocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public LocationDto update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
