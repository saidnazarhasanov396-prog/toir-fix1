package com.toir.ai.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.toir.ai.gateway.ToirAiGatewayService;
import com.toir.ai.gateway.dto.WorkOrderTextJobRequest;
import com.toir.security.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/jobs")
@Tag(name = "ai-jobs")
@ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('"
        + PermissionConstants.AI_GATEWAY_EXECUTE + "')")
public class ToirAiJobController {

    private final ToirAiGatewayService service;

    public ToirAiJobController(ToirAiGatewayService service) {
        this.service = service;
    }

    @PostMapping("/work-order/generate-draft/text")
    @Operation(summary = "Enqueue AI job: work-order draft from text")
    public ResponseEntity<JsonNode> enqueueWorkOrderText(@Valid @RequestBody WorkOrderTextJobRequest request) {
        return service.enqueueWorkOrderText(request);
    }

    @PostMapping(value = "/work-order/generate-draft/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Enqueue AI job: work-order draft from audio")
    public ResponseEntity<JsonNode> enqueueWorkOrderAudio(
            @RequestPart("equipment_id") UUID equipmentId,
            @RequestPart("audio") MultipartFile audio,
            @RequestPart(value = "webhook_url", required = false) String webhookUrl
    ) {
        return service.enqueueWorkOrderAudio(equipmentId, audio, webhookUrl);
    }

    @PostMapping(value = "/visual-inspection/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Enqueue AI job: visual inspection from video")
    public ResponseEntity<JsonNode> enqueueVisualVideo(
            @RequestPart("video") MultipartFile video,
            @RequestPart(value = "webhook_url", required = false) String webhookUrl
    ) {
        return service.enqueueVisualInspectionVideo(video, webhookUrl);
    }

    @PostMapping(value = "/visual-inspection/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Enqueue AI job: visual inspection from image")
    public ResponseEntity<JsonNode> enqueueVisualImage(
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "webhook_url", required = false) String webhookUrl
    ) {
        return service.enqueueVisualInspectionImage(image, webhookUrl);
    }

    @PostMapping("/recurrent-failure/equipment-categories")
    @Operation(summary = "Enqueue AI job: recurrent failure equipment categories")
    public ResponseEntity<JsonNode> enqueueCategories(@RequestBody(required = false) JsonNode request) {
        return service.enqueueRecurrentFailureCategories(request);
    }

    @PostMapping("/recurrent-failure/equipment/{equipmentId}/failure-evidence")
    @Operation(summary = "Enqueue AI job: recurrent failure evidence")
    public ResponseEntity<JsonNode> enqueueEvidence(
            @PathVariable UUID equipmentId,
            @RequestBody(required = false) JsonNode request
    ) {
        return service.enqueueRecurrentFailureEvidence(equipmentId, request);
    }

    @PostMapping("/recurrent-failure/equipment/{equipmentId}/cause-repair-analysis")
    @Operation(summary = "Enqueue AI job: cause-repair analysis")
    public ResponseEntity<JsonNode> enqueueCauseRepair(
            @PathVariable UUID equipmentId,
            @RequestBody(required = false) JsonNode request
    ) {
        return service.enqueueRecurrentFailureCauseRepair(equipmentId, request);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get AI background job status")
    public ResponseEntity<JsonNode> getJobStatus(@PathVariable UUID jobId) {
        return service.getJobStatus(jobId);
    }
}
