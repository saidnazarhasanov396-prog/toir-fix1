package com.toir.controller.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartLifecycleAggregateResponse;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleView;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.sparepartlifecycle.SparePartLifecycleAggregateService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/{equipmentId}/spare-parts/lifecycle")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class SparePartLifecycleAggregateController {

    private final SparePartLifecycleAggregateService service;

    @GetMapping
    @PreAuthorize("hasAuthority('*') or hasAuthority('SPARE_PART_INSTALLATION_READ')")
    public ResponseEntity<SparePartLifecycleAggregateResponse> get(
            @PathVariable UUID equipmentId,
            @RequestParam(defaultValue = "installed") String view,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.get(
                equipmentId, SparePartLifecycleView.from(view), search, status, page, size));
    }
}
