package com.toir.repaircampaign;

import com.toir.repaircampaign.dto.RepairCampaignDto;
import com.toir.repaircampaign.dto.RepairCampaignRequest;
import com.toir.repaircampaign.dto.RepairCampaignStageDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/repair-campaigns")
@Tag(name = "repair-campaigns")
public class RepairCampaignController {

    private final RepairCampaignService service;

    public RepairCampaignController(RepairCampaignService service) {
        this.service = service;
    }

    @GetMapping
    public List<RepairCampaignDto> list(@RequestParam(required = false) Integer year) {
        return year != null ? service.findByYear(year) : service.findAll();
    }

    @GetMapping("/{id}")
    public RepairCampaignDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<RepairCampaignDto> create(@Valid @RequestBody RepairCampaignRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    public RepairCampaignDto approve(@PathVariable UUID id) { return service.approve(id); }

    @PostMapping("/{id}/start")
    public RepairCampaignDto start(@PathVariable UUID id) { return service.start(id); }

    @PostMapping("/{id}/close")
    public RepairCampaignDto close(@PathVariable UUID id) { return service.close(id); }

    @PostMapping("/{id}/stages")
    public ResponseEntity<RepairCampaignStageDto> addStage(@PathVariable UUID id, @Valid @RequestBody RepairCampaignStageDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addStage(id, r));
    }

    @PostMapping("/stages/{stageId}/complete")
    public RepairCampaignStageDto completeStage(@PathVariable UUID stageId, @RequestParam double actualCost) {
        return service.completeStage(stageId, actualCost);
    }
}
