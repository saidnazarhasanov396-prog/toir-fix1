package com.toir.meter;

import com.toir.meter.dto.EquipmentMeterDto;
import com.toir.meter.dto.EquipmentMeterRequest;
import com.toir.meter.dto.MeterReadingDto;
import com.toir.meter.dto.MeterReadingRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/meters")
@Tag(name = "meters")
public class MeterController {

    private final MeterService service;
    private final MeterTriggerService triggerService;

    public MeterController(MeterService service, MeterTriggerService triggerService) {
        this.service = service;
        this.triggerService = triggerService;
    }

    @GetMapping("/triggers")
    public List<MeterTriggerService.MeterTriggerMatch> triggers(@RequestParam UUID equipmentId) {
        return triggerService.dueTriggers(equipmentId);
    }

    @GetMapping
    public List<EquipmentMeterDto> list(@RequestParam(required = false) UUID equipmentId) {
        return equipmentId != null ? service.listByEquipment(equipmentId) : service.listAll();
    }

    @GetMapping("/{id}")
    public EquipmentMeterDto get(@PathVariable UUID id) {
        return service.findMeter(id);
    }

    @PostMapping
    public ResponseEntity<EquipmentMeterDto> create(@Valid @RequestBody EquipmentMeterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMeter(request));
    }

    @PutMapping("/{id}")
    public EquipmentMeterDto update(@PathVariable UUID id, @Valid @RequestBody EquipmentMeterRequest request) {
        return service.updateMeter(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteMeter(id);
    }

    @PostMapping("/readings")
    public ResponseEntity<MeterReadingDto> addReading(@Valid @RequestBody MeterReadingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addReading(request));
    }

    @GetMapping("/{id}/readings")
    public List<MeterReadingDto> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        if (from != null && to != null) {
            return service.historyBetween(id, from, to);
        }
        return service.history(id, limit);
    }

    @DeleteMapping("/readings/{readingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReading(@PathVariable UUID readingId) {
        service.deleteReading(readingId);
    }
}
