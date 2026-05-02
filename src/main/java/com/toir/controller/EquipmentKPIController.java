package com.toir.controller;
import com.toir.dto.equipmentkpi.EquipmentKPIDto;
import com.toir.service.EquipmentKPIService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment-kpis")
@Tag(name = "equipment-kpis")
public class EquipmentKPIController {

    private final EquipmentKPIService service;

    public EquipmentKPIController(EquipmentKPIService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<EquipmentKPIDto>> list(@RequestParam UUID equipmentId) {
        return ResponseEntity.ok(service.findByEquipment(equipmentId));
    }

    @PostMapping
    public ResponseEntity<EquipmentKPIDto> record(@Valid @RequestBody EquipmentKPIDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
