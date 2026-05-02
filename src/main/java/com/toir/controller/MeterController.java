package com.toir.controller;
import com.toir.dto.meter.EquipmentMeterDto;
import com.toir.dto.meter.EquipmentMeterRequest;
import com.toir.dto.meter.MeterReadingDto;
import com.toir.dto.meter.MeterReadingRequest;
import com.toir.dto.meter.MeterTriggerMatch;
import com.toir.enums.MeterType;
import com.toir.service.MeterService;
import com.toir.service.MeterTriggerService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meters")
@Tag(name = "meters")
public class MeterController {

    private final MeterService service;
    private final MeterTriggerService triggerService;

    public MeterController(MeterService service, MeterTriggerService triggerService) {
        this.service = service;
        this.triggerService = triggerService;
    }

    @GetMapping("/triggers")
    public ResponseEntity<Page<MeterTriggerMatch>> triggers(@RequestParam UUID equipmentId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(triggerService.dueTriggers(equipmentId), page, size));
    }

    @GetMapping
    public ResponseEntity<Page<EquipmentMeterDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MeterType meterType
            , @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.listAll(search,meterType), page, size));
    }


    @GetMapping("/{id}")
    public ResponseEntity<EquipmentMeterDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findMeter(id));
    }

    @PostMapping
    public ResponseEntity<EquipmentMeterDto> create(@Valid @RequestBody EquipmentMeterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMeter(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EquipmentMeterDto> update(@PathVariable UUID id, @Valid @RequestBody EquipmentMeterRequest request) {
        return ResponseEntity.ok(service.updateMeter(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteMeter(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/readings")
    public ResponseEntity<MeterReadingDto> addReading(@Valid @RequestBody MeterReadingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addReading(request));
    }

    @GetMapping("/{id}/readings")
    public ResponseEntity<Page<MeterReadingDto>> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (from != null && to != null) {
            return ResponseEntity.ok(PaginationUtils.page(service.historyBetween(id, from, to), page, size));
        }
        return ResponseEntity.ok(PaginationUtils.page(service.history(id, limit), page, size));
    }

    @DeleteMapping("/readings/{readingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteReading(@PathVariable UUID readingId) {
        service.deleteReading(readingId);
        return ResponseEntity.noContent().build();
    }
}
