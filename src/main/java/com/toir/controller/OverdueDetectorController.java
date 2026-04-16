package com.toir.controller;
import com.toir.service.OverdueDetectorService;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/overdue")
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
