package com.toir.ai.gateway.controller;

import com.toir.ai.gateway.ToirAiGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/ai-job")
@Tag(name = "ai-job-callback")
@ConditionalOnProperty(prefix = "toir.ai.gateway", name = "enabled", havingValue = "true")
public class ToirAiJobCallbackController {

    private final ToirAiGatewayService service;

    public ToirAiJobCallbackController(ToirAiGatewayService service) {
        this.service = service;
    }

    @PostMapping("/callback")
    @Operation(summary = "AI background job webhook callback (HMAC signed, no JWT)")
    public ResponseEntity<Void> callback(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String webhookSignature = request.getHeader("X-Webhook-Signature");
        String signature = request.getHeader("X-Signature");
        String hubSignature = request.getHeader("X-Hub-Signature-256");
        return service.handleCallback(body, webhookSignature, signature, hubSignature);
    }
}
