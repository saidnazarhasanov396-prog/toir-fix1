package com.toir.controller.equipment;

import com.toir.dto.equipmentmanualattribute.BulkEquipmentManualAttributeRequest;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeDto;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentManualAttribute;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.EquipmentCategory;
import com.toir.exception.RestException;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.equipment.EquipmentManualAttributeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "equipment-manual-attributes")
@RequiredArgsConstructor
public class EquipmentManualAttributeController {

    private final EquipmentManualAttributeService service;
    private final EquipmentRepository equipmentRepository;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final ScopeAccessService scopeAccessService;

    @GetMapping("/equipment/{equipmentId}/manual-attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<List<EquipmentManualAttributeDto>> listEquipmentAttributes(@PathVariable UUID equipmentId) {
        assertCanAccessEquipment(equipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.list(equipmentId));
    }

    @PostMapping("/equipment/{equipmentId}/manual-attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<EquipmentManualAttributeDto> createEquipmentAttribute(
            @PathVariable UUID equipmentId,
            @Valid @RequestBody EquipmentManualAttributeRequest request
    ) {
        assertCanAccessEquipment(equipmentOrThrow(equipmentId));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(equipmentId, request));
    }

    @PutMapping("/equipment/{equipmentId}/manual-attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<List<EquipmentManualAttributeDto>> replaceEquipmentAttributes(
            @PathVariable UUID equipmentId,
            @Valid @RequestBody BulkEquipmentManualAttributeRequest request
    ) {
        assertCanAccessEquipment(equipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.replaceAll(equipmentId, request));
    }

    @PutMapping("/equipment/manual-attributes/{attributeId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<EquipmentManualAttributeDto> updateEquipmentAttribute(
            @PathVariable UUID attributeId,
            @Valid @RequestBody EquipmentManualAttributeRequest request
    ) {
        EquipmentManualAttribute attribute = service.findActiveAttribute(attributeId);
        assertCanAccessEquipment(equipmentOrThrow(attribute.getEquipmentId()));
        return ResponseEntity.ok(service.update(attributeId, request));
    }

    @DeleteMapping("/equipment/manual-attributes/{attributeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<Void> deleteEquipmentAttribute(@PathVariable UUID attributeId) {
        EquipmentManualAttribute attribute = service.findActiveAttribute(attributeId);
        assertCanAccessEquipment(equipmentOrThrow(attribute.getEquipmentId()));
        service.delete(attributeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/vehicles/{equipmentId}/manual-attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<List<EquipmentManualAttributeDto>> listVehicleAttributes(@PathVariable UUID equipmentId) {
        assertVehicle(equipmentId);
        return ResponseEntity.ok(service.list(equipmentId));
    }

    @PostMapping("/vehicles/{equipmentId}/manual-attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<EquipmentManualAttributeDto> createVehicleAttribute(
            @PathVariable UUID equipmentId,
            @Valid @RequestBody EquipmentManualAttributeRequest request
    ) {
        assertVehicle(equipmentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(equipmentId, request));
    }

    @PutMapping("/vehicles/{equipmentId}/manual-attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<List<EquipmentManualAttributeDto>> replaceVehicleAttributes(
            @PathVariable UUID equipmentId,
            @Valid @RequestBody BulkEquipmentManualAttributeRequest request
    ) {
        assertVehicle(equipmentId);
        return ResponseEntity.ok(service.replaceAll(equipmentId, request));
    }

    @PutMapping("/vehicles/manual-attributes/{attributeId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<EquipmentManualAttributeDto> updateVehicleAttribute(
            @PathVariable UUID attributeId,
            @Valid @RequestBody EquipmentManualAttributeRequest request
    ) {
        EquipmentManualAttribute attribute = service.findActiveAttribute(attributeId);
        assertVehicle(attribute.getEquipmentId());
        return ResponseEntity.ok(service.update(attributeId, request));
    }

    @DeleteMapping("/vehicles/manual-attributes/{attributeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<Void> deleteVehicleAttribute(@PathVariable UUID attributeId) {
        EquipmentManualAttribute attribute = service.findActiveAttribute(attributeId);
        assertVehicle(attribute.getEquipmentId());
        service.delete(attributeId);
        return ResponseEntity.noContent().build();
    }

    private void assertVehicle(UUID equipmentId) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        if (equipment.getCategory() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
        if (details.isDeleted()) {
            throw RestException.notFound("Vehicle details not found: " + equipmentId);
        }
        assertCanAccessEquipment(equipment);
    }

    private Equipment equipmentOrThrow(UUID id) {
        return equipmentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
    }

    private void assertCanAccessEquipment(Equipment equipment) {
        if (equipment.getDepartmentId() == null) {
            if (!scopeAccessService.isScopeAdmin()) {
                throw new AccessDeniedException("Access denied by equipment department scope");
            }
            return;
        }
        scopeAccessService.assertCanAccessDepartment(equipment.getDepartmentId());
    }
}
