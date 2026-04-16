package com.toir.controller;
import com.toir.service.CalibrationService;

import com.toir.dto.calibration.CalibrationRecordDto;
import com.toir.dto.calibration.CalibrationRecordRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "calibration")
public class CalibrationController {

    private final CalibrationService service;

    public CalibrationController(CalibrationService service) {
        this.service = service;
    }

    @GetMapping("/calibration-records")
    public List<CalibrationRecordDto> list(@RequestParam(required = false) UUID equipmentId) {
        return equipmentId != null ? service.findForEquipment(equipmentId) : service.findAll();
    }

    @GetMapping("/calibration-records/due")
    public List<CalibrationRecordDto> due(@RequestParam(defaultValue = "30") int withinDays) {
        return service.findDueWithin(withinDays);
    }

    @PostMapping("/calibration-records")
    public ResponseEntity<CalibrationRecordDto> create(@Valid @RequestBody CalibrationRecordRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @DeleteMapping("/calibration-records/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
