package com.toir.controller.equipment;

import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleJsonlWriter;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleStreamService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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

    private final EquipmentFleetLifecycleStreamService streamService;
    private final EquipmentFleetLifecycleJsonlWriter writer;
    private final Clock clock;

    public EquipmentFleetLifecycleController(
            EquipmentFleetLifecycleStreamService streamService,
            EquipmentFleetLifecycleJsonlWriter writer,
            @Qualifier("equipmentLifecycleClock") Clock clock) {
        this.streamService = streamService;
        this.writer = writer;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<StreamingResponseBody> get() {
        var prepared = streamService.prepare(clock.instant());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(NDJSON);
        headers.setContentDisposition(ContentDisposition.inline().filename(FILENAME, StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore().cachePrivate().noTransform());
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok()
                .headers(headers)
                .body(output -> writer.write(output, prepared));
    }
}
