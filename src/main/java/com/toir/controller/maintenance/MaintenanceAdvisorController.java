package com.toir.controller.maintenance;
import com.toir.service.maintanance.MaintenanceAdvisor;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/advisor")
@Tag(name = "advisor")
@RequiredArgsConstructor
public class MaintenanceAdvisorController {

    private final MaintenanceAdvisor advisor;


    @GetMapping("/maintenance")
    public ResponseEntity<Page<MaintenanceAdvisor.EquipmentAdvice>> list(
            @RequestParam(required = false) String urgency,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PaginationUtils.page(advisor.advice(equipmentId, urgency), page, size));
    }

    @GetMapping("/maintenance/stats")
    public ResponseEntity<MaintenanceAdvisor.MaintenanceAdviceStats> stats(
            @RequestParam(required = false) String urgency,
            @RequestParam(required = false) UUID equipmentId
    ) {
        return ResponseEntity.ok(advisor.stats(equipmentId, urgency));
    }
}
