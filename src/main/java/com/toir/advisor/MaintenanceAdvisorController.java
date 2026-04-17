package com.toir.advisor;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/advisor")
@Tag(name = "advisor")
public class MaintenanceAdvisorController {

    private final MaintenanceAdvisor advisor;

    public MaintenanceAdvisorController(MaintenanceAdvisor advisor) {
        this.advisor = advisor;
    }

    @GetMapping("/maintenance")
    public List<MaintenanceAdvisor.EquipmentAdvice> list(
            @RequestParam(required = false) String urgency) {
        List<MaintenanceAdvisor.EquipmentAdvice> all = advisor.adviceAll();
        if (urgency == null) return all;
        return all.stream().filter(a -> urgency.equalsIgnoreCase(a.urgency())).toList();
    }
}
