package com.toir.common.overdue;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/overdue")
@Tag(name = "overdue-detector")
public class OverdueDetectorController {

    private final OverdueDetectorService service;

    public OverdueDetectorController(OverdueDetectorService service) {
        this.service = service;
    }

    @PostMapping("/evaluate")
    public OverdueDetectorService.EvaluationResult evaluate() {
        return service.evaluate();
    }
}
