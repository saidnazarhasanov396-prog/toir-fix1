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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/gateway")
@Tag(name = "ai-gateway")
@ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('"
        + PermissionConstants.AI_GATEWAY_EXECUTE + "')")
public class ToirAiGatewayHealthController {

    private final ToirAiGatewayService service;

    public ToirAiGatewayHealthController(ToirAiGatewayService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @Operation(summary = "Proxy: AI service health")
    public ResponseEntity<JsonNode> health() {
        return ResponseEntity.ok(service.health());
    }
}
