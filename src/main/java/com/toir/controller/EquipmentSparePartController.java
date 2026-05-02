package com.toir.controller;
import com.toir.dto.equipmentsparepart.EquipmentSparePartDto;
import com.toir.dto.equipmentsparepart.EquipmentSparePartRequest;
import com.toir.service.EquipmentSparePartService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "equipment-spare-parts")
public class EquipmentSparePartController {

    private final EquipmentSparePartService service;

    public EquipmentSparePartController(EquipmentSparePartService service) {
        this.service = service;
    }

    @GetMapping("/equipment/{equipmentId}/spare-parts")
    public ResponseEntity<Page<EquipmentSparePartDto>> listForEquipment(@PathVariable UUID equipmentId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.listForEquipment(equipmentId), page, size));
    }

    @PostMapping("/equipment/{equipmentId}/spare-parts")
    public ResponseEntity<EquipmentSparePartDto> add(@PathVariable UUID equipmentId,
                                                     @Valid @RequestBody EquipmentSparePartRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(equipmentId, r));
    }

    @PutMapping("/equipment-spare-parts/{id}")
    public ResponseEntity<EquipmentSparePartDto> update(@PathVariable UUID id, @Valid @RequestBody EquipmentSparePartRequest r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/equipment-spare-parts/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/spare-parts/{sparePartId}/equipment")
    public ResponseEntity<Page<EquipmentSparePartDto>> listForSparePart(@PathVariable UUID sparePartId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.listForSparePart(sparePartId), page, size));
    }
}
