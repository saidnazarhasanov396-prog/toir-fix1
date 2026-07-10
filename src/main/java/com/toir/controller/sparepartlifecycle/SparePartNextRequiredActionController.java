package com.toir.controller.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartNextRequiredActionsDto;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.sparepartlifecycle.SparePartNextRequiredActionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/{equipmentId}/next-required-actions")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class SparePartNextRequiredActionController {

    private final SparePartNextRequiredActionService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_DUE_READ')")
    public ResponseEntity<SparePartNextRequiredActionsDto> get(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.get(equipmentId));
    }
}
