package com.toir.controller.equipment;

import com.toir.dto.equipmentmaintenance.EquipmentMaintenanceProfileDto;
import com.toir.dto.equipmentmaintenance.EquipmentMaintenanceRuleDto;
import com.toir.dto.equipmentmaintenance.EquipmentMaintenanceRuleRequest;
import com.toir.service.maintanance.EquipmentMaintenanceProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/{equipmentId}/maintenance-profile")
@RequiredArgsConstructor
public class EquipmentMaintenanceProfileController {

    private final EquipmentMaintenanceProfileService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<EquipmentMaintenanceProfileDto> getProfile(
            @PathVariable UUID equipmentId,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return ResponseEntity.ok(service.getProfile(equipmentId, lang != null ? lang : acceptLanguage));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<EquipmentMaintenanceRuleDto> createRule(
            @PathVariable UUID equipmentId,
            @Valid @RequestBody EquipmentMaintenanceRuleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRule(equipmentId, request));
    }

    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<EquipmentMaintenanceRuleDto> updateRule(
            @PathVariable UUID equipmentId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody EquipmentMaintenanceRuleRequest request
    ) {
        return ResponseEntity.ok(service.updateRule(equipmentId, ruleId, request));
    }

    @DeleteMapping("/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<Void> deleteRule(
            @PathVariable UUID equipmentId,
            @PathVariable UUID ruleId
    ) {
        service.deleteRule(equipmentId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
