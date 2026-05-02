package com.toir.controller;
import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.entity.RcmSnapshot;
import com.toir.service.RcmAutoPlannerService;
import com.toir.service.RcmService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<List<EquipmentRiskScore>> list(@RequestParam(defaultValue = "0") int top) {
        return ResponseEntity.ok(top > 0 ? service.topN(top) : service.computeAll());
    }

    @PostMapping("/snapshot")
    public ResponseEntity<List<RcmSnapshot>> capture() {
        return ResponseEntity.ok(service.captureSnapshot());
    }

    @GetMapping("/snapshot/{equipmentId}")
    public ResponseEntity<List<RcmSnapshot>> history(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.historyFor(equipmentId));
    }

    @PostMapping("/auto-plan")
    public ResponseEntity<RcmAutoPlannerService.AutoPlanResult> autoPlan(@RequestParam(defaultValue = "30") int riskThreshold,
                                                         @RequestParam(required = false) UUID planId) {
        return ResponseEntity.ok(autoPlannerService.generate(riskThreshold, planId));
    }
}
