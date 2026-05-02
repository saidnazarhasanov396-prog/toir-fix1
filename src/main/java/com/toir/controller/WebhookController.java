package com.toir.controller;
import com.toir.dto.webhook.WebhookDto;
import com.toir.dto.webhook.WebhookTestResponse;
import com.toir.entity.WebhookEventLog;
import com.toir.entity.WebhookSubscription;
import com.toir.service.WebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "webhooks")
public class WebhookController {

    private final WebhookService service;

    public WebhookController(WebhookService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<WebhookDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(service.findAll(search,active));
    }

    @PostMapping
    public ResponseEntity<WebhookDto> create(@RequestBody WebhookSubscription sub) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(sub));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WebhookDto> update(@PathVariable UUID id, @RequestBody WebhookSubscription patch) {
        return ResponseEntity.ok(service.update(id, patch));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<WebhookEventLog>> events(@PathVariable UUID id) {
        return ResponseEntity.ok(service.recentForSubscription(id));
    }

    @PostMapping("/test")
    public ResponseEntity<WebhookTestResponse> test(@RequestParam String eventCode,
                                    @RequestBody(required = false) Object payload) {
        int delivered = service.publish(eventCode, payload != null ? payload : Map.of("test", true));
        return ResponseEntity.ok(new WebhookTestResponse(delivered));
    }
}
