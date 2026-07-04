package com.toir.controller;

import com.toir.dto.oee.OeeFilter;
import com.toir.dto.oee.OeeRecordDto;
import com.toir.dto.oee.OeeSummary;
import com.toir.service.OeeService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/oee")
@Tag(name = "oee")
@RequiredArgsConstructor
public class OeeController {

    private final OeeService service;

    @GetMapping
    public ResponseEntity<Page<OeeRecordDto>> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String equipmentSearch,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Double minOee,
            @RequestParam(required = false) Double maxOee,
            @RequestParam(required = false) Double minAvailability,
            @RequestParam(required = false) Double maxAvailability,
            @RequestParam(required = false) Double minQuality,
            @RequestParam(required = false) Double maxQuality,
            @RequestParam(defaultValue = "shiftStart") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        OeeFilter filter = new OeeFilter(equipmentId, equipmentSearch, departmentId, equipmentTypeId, from, to,
                minOee, maxOee, minAvailability, maxAvailability, minQuality, maxQuality, sortBy, sortDir);
        return ResponseEntity.ok(PaginationUtils.page(service.list(filter), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OeeRecordDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/summary")
    public ResponseEntity<OeeSummary> summary(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String equipmentSearch,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Double minOee,
            @RequestParam(required = false) Double maxOee,
            @RequestParam(required = false) Double minAvailability,
            @RequestParam(required = false) Double maxAvailability,
            @RequestParam(required = false) Double minQuality,
            @RequestParam(required = false) Double maxQuality) {
        OeeFilter filter = new OeeFilter(equipmentId, equipmentSearch, departmentId, equipmentTypeId, from, to,
                minOee, maxOee, minAvailability, maxAvailability, minQuality, maxQuality, "shiftStart", "desc");
        return ResponseEntity.ok(service.summary(filter));
    }
}
