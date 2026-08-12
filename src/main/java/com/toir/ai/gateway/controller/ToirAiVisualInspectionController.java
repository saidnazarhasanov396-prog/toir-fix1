package com.toir.ai.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.toir.ai.gateway.ToirAiGatewayService;
import com.toir.security.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai/visual-inspection")
@Tag(name = "ai-visual-inspection")
@ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('"
        + PermissionConstants.AI_GATEWAY_EXECUTE + "')")
public class ToirAiVisualInspectionController {

    private final ToirAiGatewayService service;

    public ToirAiVisualInspectionController(ToirAiGatewayService service) {
        this.service = service;
    }

    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Proxy: visual inspection from video via AI")
    public ResponseEntity<JsonNode> inspectVideo(@RequestPart("video") MultipartFile video) {
        return ResponseEntity.ok(service.visualInspectionVideo(video));
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Proxy: visual inspection from image via AI")
    public ResponseEntity<JsonNode> inspectImage(@RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(service.visualInspectionImage(image));
    }
}
