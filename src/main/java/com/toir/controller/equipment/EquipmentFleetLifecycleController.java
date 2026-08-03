package com.toir.controller.equipment;

import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleJsonlWriter;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleStreamService;
import com.toir.util.RequestContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/equipment/lifecycle-context")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
public class EquipmentFleetLifecycleController {

    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");
    private static final String FILENAME = "equipment-fleet-lifecycle-v1.jsonl";
    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final EquipmentFleetLifecycleStreamService streamService;
    private final EquipmentFleetLifecycleJsonlWriter writer;
    private final Clock clock;
    private final RequestContext requestContext;

    public EquipmentFleetLifecycleController(
            EquipmentFleetLifecycleStreamService streamService,
            EquipmentFleetLifecycleJsonlWriter writer,
            @Qualifier("equipmentLifecycleClock") Clock clock,
            RequestContext requestContext) {
        this.streamService = streamService;
        this.writer = writer;
        this.clock = clock;
        this.requestContext = requestContext;
    }

    @GetMapping
    public ResponseEntity<StreamingResponseBody> get() {
        String correlationId = resolveCorrelationId(requestContext.getCorrelationId());
        var prepared = streamService.prepare(clock.instant(), correlationId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(NDJSON);
        headers.setContentDisposition(ContentDisposition.inline().filename(FILENAME, StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore().cachePrivate().noTransform());
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok()
                .headers(headers)
                .body(output -> writer.write(output, prepared));
    }

    private static String resolveCorrelationId(String supplied) {
        if (supplied != null) {
            String candidate = supplied.trim();
            if (SAFE_CORRELATION_ID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString();
    }
}
