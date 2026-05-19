package com.toir.controller;
import com.toir.dto.conditionreading.ConditionReadingDto;
import com.toir.dto.conditionreading.ConditionReadingRequest;
import com.toir.enums.ConditionParameter;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.ConditionReadingService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "condition-readings")
@RequiredArgsConstructor
public class ConditionReadingController {

    private final ConditionReadingService service;
    private final SecurityScope securityScope;

    @GetMapping("/equipment/{equipmentId}/condition-readings")
    public ResponseEntity<Page<ConditionReadingDto>> list(@PathVariable UUID equipmentId,
                                          @RequestParam(required = false) ConditionParameter parameter, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findForEquipment(equipmentId, parameter), page, size));
    }

    @PostMapping("/equipment/{equipmentId}/condition-readings")
    public ResponseEntity<ConditionReadingDto> record(@PathVariable UUID equipmentId,
                                                      @Valid @RequestBody ConditionReadingRequest r) {
        AuthenticatedUser u = securityScope.currentUser();
        UUID userId = u != null && u.id() != null ? UUID.fromString(u.id()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(equipmentId, r, userId));
    }

    @DeleteMapping("/condition-readings/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/condition-readings/alarms")
    public ResponseEntity<Page<ConditionReadingDto>> alarms(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAlarms(), page, size));
    }
}
