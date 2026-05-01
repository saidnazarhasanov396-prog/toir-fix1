package com.toir.controller;
import com.toir.dto.webhook.WebhookTestResponse;
import com.toir.entity.WebhookEventLog;
import com.toir.service.WebhookService;
import com.toir.entity.WebhookSubscription;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "webhooks")
public class WebhookController {

    private final WebhookService service;

    public WebhookController(WebhookService service) {
        this.service = service;
    }

    @GetMapping
    public List<WebhookSubscription> list() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<WebhookSubscription> create(@RequestBody WebhookSubscription sub) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(sub));
    }

    @PutMapping("/{id}")
    public WebhookSubscription update(@PathVariable UUID id, @RequestBody WebhookSubscription patch) {
        return service.update(id, patch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/events")
    public List<WebhookEventLog> events(@PathVariable UUID id) {
        return service.recentForSubscription(id);
    }

    @PostMapping("/test")
    public WebhookTestResponse test(@RequestParam String eventCode,
                                    @RequestBody(required = false) Object payload) {
        int delivered = service.publish(eventCode, payload != null ? payload : Map.of("test", true));
        return new WebhookTestResponse(delivered);
    }
}
