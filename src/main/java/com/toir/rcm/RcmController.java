package com.toir.rcm;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rcm")
@Tag(name = "rcm")
public class RcmController {

    private final RcmService service;
    private final RcmAutoPlannerService autoPlannerService;

    public RcmController(RcmService service, RcmAutoPlannerService autoPlannerService) {
        this.service = service;
        this.autoPlannerService = autoPlannerService;
    }

    @GetMapping("/risk-scores")
    public List<EquipmentRiskScore> list(@RequestParam(defaultValue = "0") int top) {
        return top > 0 ? service.topN(top) : service.computeAll();
    }

    @PostMapping("/snapshot")
    public List<RcmSnapshot> capture() {
        return service.captureSnapshot();
    }

    @GetMapping("/snapshot/{equipmentId}")
    public List<RcmSnapshot> history(@PathVariable UUID equipmentId) {
        return service.historyFor(equipmentId);
    }

    @PostMapping("/auto-plan")
    public RcmAutoPlannerService.AutoPlanResult autoPlan(@RequestParam(defaultValue = "30") int riskThreshold,
                                                         @RequestParam(required = false) UUID planId) {
        return autoPlannerService.generate(riskThreshold, planId);
    }
}
