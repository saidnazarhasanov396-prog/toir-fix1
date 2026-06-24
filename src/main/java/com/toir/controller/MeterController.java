package com.toir.controller;
import com.toir.dto.meter.*;
import com.toir.enums.MeterType;
import com.toir.service.MeterService;
import com.toir.service.MeterTriggerService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meters")
@Tag(name = "meters")
@RequiredArgsConstructor
public class MeterController {

    private static final String METER_READ_AUTH =
            "hasAnyAuthority('read','METER_READ','SYSTEM_ADMIN','*')";
    private static final String METER_CREATE_AUTH =
            "hasAnyAuthority('METER_CREATE','SYSTEM_ADMIN','*')";
    private static final String METER_UPDATE_AUTH =
            "hasAnyAuthority('METER_UPDATE','SYSTEM_ADMIN','*')";
    private static final String METER_DELETE_AUTH =
            "hasAnyAuthority('METER_DELETE','SYSTEM_ADMIN','*')";
    private static final String METER_READING_CREATE_AUTH =
            "hasAnyAuthority('METER_READING_CREATE','SYSTEM_ADMIN','*')";
    private static final String METER_READING_DELETE_AUTH =
            "hasAnyAuthority('METER_READING_DELETE','SYSTEM_ADMIN','*')";

    private final MeterService service;
    private final MeterTriggerService triggerService;

    @GetMapping("/triggers")
    @PreAuthorize(METER_READ_AUTH)
    public ResponseEntity<Page<MeterTriggerMatch>> triggers(@RequestParam(required = false) UUID equipmentId,
                                                            @RequestParam(required = false) String equipmentSearch,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(triggerService.dueTriggers(equipmentId, equipmentSearch), page, size));
    }

    @GetMapping
    @PreAuthorize(METER_READ_AUTH)
    public ResponseEntity<Page<EquipmentMeterDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MeterType meterType,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String equipmentSearch,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir) {
        if (sortBy == null || sortBy.isBlank()) {
            return ResponseEntity.ok(PaginationUtils.page(
                    service.listAll(search, meterType, equipmentId, equipmentSearch),
                    page,
                    size));
        }
        return ResponseEntity.ok(PaginationUtils.page(
                service.listAll(search, meterType, equipmentId, equipmentSearch, sortBy, sortDir),
                page,
                size));
    }

    @GetMapping("/stats")
    @PreAuthorize(METER_READ_AUTH)
    public ResponseEntity<MeterStatsResponse> stats(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MeterType meterType,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String equipmentSearch) {
        return ResponseEntity.ok(service.getStats(search, meterType, equipmentId, equipmentSearch));
    }


    @GetMapping("/{id}")
    @PreAuthorize(METER_READ_AUTH)
    public ResponseEntity<EquipmentMeterDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findMeter(id));
    }

    @PostMapping
    @PreAuthorize(METER_CREATE_AUTH)
    public ResponseEntity<EquipmentMeterDto> create(@Valid @RequestBody EquipmentMeterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMeter(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(METER_UPDATE_AUTH)
    public ResponseEntity<EquipmentMeterDto> update(@PathVariable UUID id, @Valid @RequestBody EquipmentMeterRequest request) {
        return ResponseEntity.ok(service.updateMeter(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(METER_DELETE_AUTH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteMeter(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/readings")
    @PreAuthorize(METER_READING_CREATE_AUTH)
    public ResponseEntity<MeterReadingDto> addReading(@Valid @RequestBody MeterReadingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addReading(request));
    }

    @GetMapping("/readings")
    @PreAuthorize(METER_READ_AUTH)
    public ResponseEntity<Page<MeterReadingDto>> readings(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listReadings(search, sort, direction, page, size));
    }

    @GetMapping("/{id}/readings")
    @PreAuthorize(METER_READ_AUTH)
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
    @PreAuthorize(METER_READING_DELETE_AUTH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteReading(@PathVariable UUID readingId) {
        service.deleteReading(readingId);
        return ResponseEntity.noContent().build();
    }
}
