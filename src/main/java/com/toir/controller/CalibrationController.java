package com.toir.controller;
import com.toir.dto.calibration.CalibrationRecordDto;
import com.toir.dto.calibration.CalibrationRecordRequest;
import com.toir.service.CalibrationService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "calibration")
@RequiredArgsConstructor
public class CalibrationController {

    private static final String CALIBRATION_RECORD_READ_AUTH =
            "hasAnyAuthority('read','CALIBRATION_RECORD_READ','SYSTEM_ADMIN','*')";
    private static final String CALIBRATION_RECORD_CREATE_AUTH =
            "hasAnyAuthority('CALIBRATION_RECORD_CREATE','SYSTEM_ADMIN','*')";
    private static final String CALIBRATION_RECORD_UPDATE_AUTH =
            "hasAnyAuthority('CALIBRATION_RECORD_UPDATE','SYSTEM_ADMIN','*')";
    private static final String CALIBRATION_RECORD_DELETE_AUTH =
            "hasAnyAuthority('CALIBRATION_RECORD_DELETE','SYSTEM_ADMIN','*')";

    private final CalibrationService service;

    @GetMapping("/calibration-records")
    @PreAuthorize(CALIBRATION_RECORD_READ_AUTH)
    public ResponseEntity<Page<CalibrationRecordDto>> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (equipmentId != null) {
            return ResponseEntity.ok(PaginationUtils.page(service.findForEquipment(equipmentId), page, size));
        }
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(search), page, size));
    }

    @GetMapping("/calibration-records/{id}")
    @PreAuthorize(CALIBRATION_RECORD_READ_AUTH)
    public ResponseEntity<CalibrationRecordDto> getById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok().body(service.findById(id));
    }

    @GetMapping("/calibration-records/due")
    @PreAuthorize(CALIBRATION_RECORD_READ_AUTH)
    public ResponseEntity<Page<CalibrationRecordDto>> due(@RequestParam(defaultValue = "30") int withinDays, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findDueWithin(withinDays), page, size));
    }

    @PostMapping("/calibration-records")
    @PreAuthorize(CALIBRATION_RECORD_CREATE_AUTH)
    public ResponseEntity<CalibrationRecordDto> create(@Valid @RequestBody CalibrationRecordRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/calibration-records/{id}")
    @PreAuthorize(CALIBRATION_RECORD_UPDATE_AUTH)
    public ResponseEntity<CalibrationRecordDto> update(@PathVariable UUID id, @Valid @RequestBody CalibrationRecordRequest r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/calibration-records/{id}")
    @PreAuthorize(CALIBRATION_RECORD_DELETE_AUTH)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
