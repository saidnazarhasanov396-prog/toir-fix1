package com.toir.controller.equipment;

import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest;
import com.toir.dto.equipmentattribute.EquipmentAttributeOptionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeOptionSourceDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeOptionSourceRequest;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueHistoryDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.equipment.EquipmentAttributeService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "equipment-attributes")
@RequiredArgsConstructor
public class EquipmentAttributeController {

    private final EquipmentAttributeService service;
    private final ScopeAccessService scopeAccessService;
    private final EquipmentRepository equipmentRepository;

    @GetMapping("/equipment-attribute-option-sources")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TYPE_READ')")
    public ResponseEntity<Page<EquipmentAttributeOptionSourceDto>> listOptionSources(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer pageSize) {
        int effectiveSize = pageSize != null ? pageSize : size;
        return ResponseEntity.ok(PaginationUtils.page(service.findOptionSources(search), page, effectiveSize));
    }

    @PostMapping("/equipment-attribute-option-sources")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TYPE_UPDATE')")
    public ResponseEntity<EquipmentAttributeOptionSourceDto> createOptionSource(
            @Valid @RequestBody EquipmentAttributeOptionSourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOptionSource(request));
    }

    @GetMapping("/equipment-attribute-option-sources/{sourceId}/options")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TYPE_READ')")
    public ResponseEntity<List<EquipmentAttributeOptionDto>> listOptions(@PathVariable UUID sourceId) {
        return ResponseEntity.ok(service.findOptions(sourceId));
    }

    @PutMapping("/equipment-attribute-option-sources/{sourceId}/options")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TYPE_UPDATE')")
    public ResponseEntity<List<EquipmentAttributeOptionDto>> replaceOptions(
            @PathVariable UUID sourceId,
            @RequestBody List<EquipmentAttributeOptionDto> request) {
        return ResponseEntity.ok(service.replaceOptions(sourceId, request));
    }

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

    @PostMapping("/equipment-types/{equipmentTypeId}/attributes/batch")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_TYPE_UPDATE')")
    public ResponseEntity<List<EquipmentAttributeDefinitionDto>> createDefinitionsBatch(
            @PathVariable UUID equipmentTypeId,
            @Valid @RequestBody List<@Valid EquipmentAttributeDefinitionRequest> request) {
        validateBatchRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDefinitionsBatch(equipmentTypeId, request));
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
        assertCanAccessEquipment(equipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.findValues(equipmentId));
    }

    @GetMapping("/equipment/{equipmentId}/attributes/history")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<EquipmentAttributeValueHistoryDto>> valueHistory(
            @PathVariable UUID equipmentId,
            @RequestParam(required = false) UUID attributeDefinitionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        assertCanAccessEquipment(equipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.findValueHistory(
                equipmentId,
                attributeDefinitionId,
                PageRequest.of(Math.max(0, page), Math.max(1, size))
        ));
    }

    @PutMapping("/equipment/{equipmentId}/attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<List<EquipmentAttributeValueDto>> replaceValues(
            @PathVariable UUID equipmentId,
            @RequestBody List<EquipmentAttributeValueRequest> request) {
        assertCanAccessEquipment(equipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.replaceValues(equipmentId, request));
    }

    @PostMapping("/equipment/{equipmentId}/attributes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<List<EquipmentAttributeValueDto>> saveValues(
            @PathVariable UUID equipmentId,
            @RequestBody List<EquipmentAttributeValueRequest> request) {
        assertCanAccessEquipment(equipmentOrThrow(equipmentId));
        return ResponseEntity.ok(service.replaceValues(equipmentId, request));
    }

    private Equipment equipmentOrThrow(UUID equipmentId) {
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
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

    private void validateBatchRequest(List<EquipmentAttributeDefinitionRequest> request) {
        if (request == null) {
            throw RestException.badRequest("Equipment attribute definition batch is required");
        }
        if (request.isEmpty()) {
            throw RestException.badRequest("Equipment attribute definition batch must not be empty");
        }

        Set<String> normalizedKeys = new HashSet<>();
        for (EquipmentAttributeDefinitionRequest item : request) {
            if (item == null) {
                throw RestException.badRequest("Equipment attribute definition request is required");
            }
            if (item.key() == null || item.key().isBlank()) {
                throw RestException.badRequest("Attribute key is required");
            }
            if (item.label() == null || item.label().isBlank()) {
                throw RestException.badRequest("Attribute label is required");
            }
            if (item.dataType() == null) {
                throw RestException.badRequest("Attribute dataType is required");
            }
            if (item.minValue() != null && item.maxValue() != null && item.minValue() > item.maxValue()) {
                throw RestException.badRequest("minValue cannot be greater than maxValue");
            }

            String normalizedKey = item.key().trim().toLowerCase(Locale.ROOT);
            if (!normalizedKeys.add(normalizedKey)) {
                throw RestException.badRequest("Duplicate equipment attribute definition key in batch: " + normalizedKey);
            }
        }
    }
}
