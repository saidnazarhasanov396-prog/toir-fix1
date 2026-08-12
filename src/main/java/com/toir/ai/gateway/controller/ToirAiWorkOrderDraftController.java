package com.toir.ai.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.toir.ai.gateway.ToirAiGatewayService;
import com.toir.ai.gateway.dto.WorkOrderDraftTextRequest;
import com.toir.security.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/work-orders")
@Tag(name = "ai-work-order-draft")
@ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('"
        + PermissionConstants.AI_GATEWAY_EXECUTE + "')")
public class ToirAiWorkOrderDraftController {

    private final ToirAiGatewayService service;

    public ToirAiWorkOrderDraftController(ToirAiGatewayService service) {
        this.service = service;
    }

    @PostMapping("/generate-draft/text")
    @Operation(summary = "Proxy: generate work-order draft from text via AI")
    public ResponseEntity<JsonNode> generateDraftFromText(@Valid @RequestBody WorkOrderDraftTextRequest request) {
        return ResponseEntity.ok(service.generateDraftFromText(request));
    }

    @PostMapping(value = "/generate-draft/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Proxy: generate work-order draft from audio via AI")
    public ResponseEntity<JsonNode> generateDraftFromAudio(
            @RequestPart("equipment_id") UUID equipmentId,
            @RequestPart("audio") MultipartFile audio
    ) {
        return ResponseEntity.ok(service.generateDraftFromAudio(equipmentId, audio));
    }
}
