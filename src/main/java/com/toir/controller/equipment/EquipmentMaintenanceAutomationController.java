package com.toir.controller.equipment;

import com.toir.enums.MaintenanceTriggerSource;
import com.toir.service.maintanance.MaintenanceAutomationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/{equipmentId}/maintenance")
@RequiredArgsConstructor
public class EquipmentMaintenanceAutomationController {

    private final MaintenanceAutomationService automationService;

    @PostMapping("/recalculate")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_AUTOMATION_RUN') or hasAuthority('EQUIPMENT_UPDATE')")
    public ResponseEntity<Void> recalculate(@PathVariable UUID equipmentId) {
        automationService.evaluateEquipment(equipmentId, MaintenanceTriggerSource.MANUAL_RECALCULATION);
        return ResponseEntity.noContent().build();
    }
}
