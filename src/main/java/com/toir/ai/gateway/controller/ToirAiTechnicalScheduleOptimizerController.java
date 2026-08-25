package com.toir.ai.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.toir.ai.gateway.ToirAiGatewayService;
import com.toir.security.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/technical-schedule-optimizer")
@Tag(name = "ai-technical-schedule-optimizer")
@ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('"
        + PermissionConstants.AI_GATEWAY_EXECUTE + "')")
public class ToirAiTechnicalScheduleOptimizerController {

    private final ToirAiGatewayService service;

    public ToirAiTechnicalScheduleOptimizerController(ToirAiGatewayService service) {
        this.service = service;
    }

    @GetMapping("/equipment/{equipmentId}/maintenance-kind-intervals")
    @Operation(summary = "Proxy: MaintenanceKind intervals via Weibull risk model")
    public ResponseEntity<JsonNode> maintenanceKindIntervals(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.maintenanceKindIntervals(equipmentId));
    }
}
