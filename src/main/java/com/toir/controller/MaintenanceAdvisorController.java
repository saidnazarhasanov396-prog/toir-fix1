package com.toir.controller;
import com.toir.service.MaintenanceAdvisor;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/advisor")
@Tag(name = "advisor")
public class MaintenanceAdvisorController {

    private final MaintenanceAdvisor advisor;

    public MaintenanceAdvisorController(MaintenanceAdvisor advisor) {
        this.advisor = advisor;
    }

    @GetMapping("/maintenance")
    public ResponseEntity<Page<MaintenanceAdvisor.EquipmentAdvice>> list(
            @RequestParam(required = false) String urgency, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        List<MaintenanceAdvisor.EquipmentAdvice> all = advisor.adviceAll();
        if (urgency == null) return ResponseEntity.ok(PaginationUtils.page(all, page, size));
        return ResponseEntity.ok(PaginationUtils.page(all.stream().filter(a -> urgency.equalsIgnoreCase(a.urgency())).toList(), page, size));
    }
}
