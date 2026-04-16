package com.toir.equipmentsparepart;

import com.toir.equipmentsparepart.dto.EquipmentSparePartDto;
import com.toir.equipmentsparepart.dto.EquipmentSparePartRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "equipment-spare-parts")
public class EquipmentSparePartController {

    private final EquipmentSparePartService service;

    public EquipmentSparePartController(EquipmentSparePartService service) {
        this.service = service;
    }

    @GetMapping("/equipment/{equipmentId}/spare-parts")
    public List<EquipmentSparePartDto> listForEquipment(@PathVariable UUID equipmentId) {
        return service.listForEquipment(equipmentId);
    }

    @PostMapping("/equipment/{equipmentId}/spare-parts")
    public ResponseEntity<EquipmentSparePartDto> add(@PathVariable UUID equipmentId,
                                                     @Valid @RequestBody EquipmentSparePartRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(equipmentId, r));
    }

    @PutMapping("/equipment-spare-parts/{id}")
    public EquipmentSparePartDto update(@PathVariable UUID id, @Valid @RequestBody EquipmentSparePartRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/equipment-spare-parts/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/spare-parts/{sparePartId}/equipment")
    public List<EquipmentSparePartDto> listForSparePart(@PathVariable UUID sparePartId) {
        return service.listForSparePart(sparePartId);
    }
}
