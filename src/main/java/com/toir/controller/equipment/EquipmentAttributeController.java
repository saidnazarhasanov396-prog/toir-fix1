package com.toir.controller.equipment;

import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.service.equipment.EquipmentAttributeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "equipment-attributes")
@RequiredArgsConstructor
public class EquipmentAttributeController {

    private final EquipmentAttributeService service;

    @GetMapping("/equipment-types/{equipmentTypeId}/attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TYPE_READ')")
    public ResponseEntity<List<EquipmentAttributeDefinitionDto>> listDefinitions(@PathVariable UUID equipmentTypeId) {
        return ResponseEntity.ok(service.findDefinitions(equipmentTypeId));
    }

    @PostMapping("/equipment-types/{equipmentTypeId}/attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TYPE_UPDATE')")
    public ResponseEntity<EquipmentAttributeDefinitionDto> createDefinition(
            @PathVariable UUID equipmentTypeId,
            @Valid @RequestBody EquipmentAttributeDefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDefinition(equipmentTypeId, request));
    }

    @PutMapping("/equipment-types/{equipmentTypeId}/attributes/{attributeId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TYPE_UPDATE')")
    public ResponseEntity<EquipmentAttributeDefinitionDto> updateDefinition(
            @PathVariable UUID equipmentTypeId,
            @PathVariable UUID attributeId,
            @Valid @RequestBody EquipmentAttributeDefinitionRequest request) {
        return ResponseEntity.ok(service.updateDefinition(equipmentTypeId, attributeId, request));
    }

    @DeleteMapping("/equipment-types/{equipmentTypeId}/attributes/{attributeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TYPE_UPDATE')")
    public ResponseEntity<Void> deleteDefinition(@PathVariable UUID equipmentTypeId,
                                                 @PathVariable UUID attributeId) {
        service.deleteDefinition(equipmentTypeId, attributeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/equipment/{equipmentId}/attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<List<EquipmentAttributeValueDto>> listValues(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.findValues(equipmentId));
    }

    @PutMapping("/equipment/{equipmentId}/attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<List<EquipmentAttributeValueDto>> replaceValues(
            @PathVariable UUID equipmentId,
            @RequestBody List<EquipmentAttributeValueRequest> request) {
        return ResponseEntity.ok(service.replaceValues(equipmentId, request));
    }

    @PostMapping("/equipment/{equipmentId}/attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<List<EquipmentAttributeValueDto>> saveValues(
            @PathVariable UUID equipmentId,
            @RequestBody List<EquipmentAttributeValueRequest> request) {
        return ResponseEntity.ok(service.replaceValues(equipmentId, request));
    }
}
