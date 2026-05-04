package com.toir.controller;

import com.toir.service.InboundErpService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integrations/inbound")
@Tag(name = "inbound-integrations")
@RequiredArgsConstructor
public class InboundErpController {

    private final InboundErpService inboundErpService;

    @PostMapping("/spare-parts")
    public ResponseEntity<InboundErpService.UpsertResult> upsertSpareParts(
            @Valid @RequestBody List<InboundErpService.SparePartImport> items
    ) {
        return ResponseEntity.ok(inboundErpService.upsertSpareParts(items));
    }

    @PostMapping("/departments")
    public ResponseEntity<InboundErpService.UpsertResult> upsertDepartments(
            @Valid @RequestBody List<InboundErpService.DepartmentImport> items
    ) {
        return ResponseEntity.ok(inboundErpService.upsertDepartments(items));
    }
}
