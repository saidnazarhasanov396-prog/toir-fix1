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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/recurrent-failure")
@Tag(name = "ai-recurrent-failure")
@ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('"
        + PermissionConstants.AI_GATEWAY_EXECUTE + "')")
public class ToirAiRecurrentFailureController {

    private final ToirAiGatewayService service;

    public ToirAiRecurrentFailureController(ToirAiGatewayService service) {
        this.service = service;
    }

    @GetMapping("/equipment-categories")
    @Operation(summary = "Proxy: equipment failure categories via AI")
    public ResponseEntity<JsonNode> equipmentCategories(
            @RequestParam(value = "equipment_id", required = false) UUID equipmentId
    ) {
        return ResponseEntity.ok(service.equipmentFailureCategories(equipmentId));
    }

    @GetMapping("/equipment-categories/stream")
    @Operation(summary = "Proxy: stream equipment failure categories via AI")
    public ResponseEntity<byte[]> equipmentCategoriesStream(
            @RequestParam(value = "equipment_id", required = false) UUID equipmentId
    ) {
        return service.equipmentFailureCategoriesStream(equipmentId);
    }

    @GetMapping("/equipment/{equipmentId}/failure-evidence")
    @Operation(summary = "Proxy: equipment failure evidence via AI")
    public ResponseEntity<JsonNode> failureEvidence(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.equipmentFailureEvidence(equipmentId));
    }

    @GetMapping("/equipment/{equipmentId}/cause-repair-analysis")
    @Operation(summary = "Proxy: equipment cause-repair analysis via AI")
    public ResponseEntity<JsonNode> causeRepairAnalysis(
            @PathVariable UUID equipmentId,
            @RequestParam(value = "work_order_id", required = false) UUID workOrderId
    ) {
        return ResponseEntity.ok(service.equipmentCauseRepairAnalysis(equipmentId, workOrderId));
    }
}
