package com.toir.controller.equipment;

import com.toir.dto.equipmentcost.EquipmentCostDrilldownResponse;
import com.toir.enums.ActualCostSourceType;
import com.toir.service.EquipmentCostDrilldownService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/equipment")
@Tag(name = "equipment-cost-drilldown")
@RequiredArgsConstructor
public class EquipmentCostDrilldownController {

    private final EquipmentCostDrilldownService service;

    @GetMapping("/{equipmentId}/cost-drilldown")
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            (hasAuthority('EQUIPMENT_READ') and hasAuthority('ACTUAL_COST_READ'))
            """)
    public ResponseEntity<EquipmentCostDrilldownResponse> getCostDrilldown(
            @PathVariable UUID equipmentId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ActualCostSourceType sourceType
    ) {
        return ResponseEntity.ok(service.getDrilldown(equipmentId, from, to, categoryId, sourceType));
    }
}
