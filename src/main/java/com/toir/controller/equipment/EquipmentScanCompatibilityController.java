package com.toir.controller.equipment;

import com.toir.dto.equipmentlabel.EquipmentLabelResponse;
import com.toir.entity.equipment.Equipment;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scan/equipment")
@Tag(name = "equipment-scan-compat")
@RequiredArgsConstructor
public class EquipmentScanCompatibilityController {

    private final EquipmentRepository repository;

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentLabelResponse> resolveByEquipmentId(@PathVariable UUID id) {
        Equipment equipment = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
        return ResponseEntity.ok(buildPayload(equipment));
    }

    private EquipmentLabelResponse buildPayload(Equipment equipment) {
        String qrPayload = String.format("toir://equipment/%s?code=%s&inv=%s",
                equipment.getId(), equipment.getCode(), equipment.getInventoryNumber());

        EquipmentLabelResponse.EquipmentRef ref = new EquipmentLabelResponse.EquipmentRef(
                equipment.getId(),
                equipment.getCode(),
                equipment.getName(),
                equipment.getInventoryNumber(),
                equipment.getTechnicalNumber(),
                equipment.getSerialNumber(),
                equipment.getModel(),
                equipment.getEquipmentTypeId(),
                equipment.getDepartmentId(),
                equipment.getLocationId(),
                equipment.getParentId(),
                equipment.getManufacturer(),
                equipment.getStatus(),
                equipment.getCategory()
        );

        return new EquipmentLabelResponse(
                ref,
                equipment.getId(),
                equipment.getCode(),
                equipment.getInventoryNumber(),
                equipment.getName(),
                qrPayload,
                Instant.now().toString(),
                List.of(ref)
        );
    }
}

