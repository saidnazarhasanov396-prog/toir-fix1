package com.toir.controller;
import com.toir.entity.ConditionParameter;
import com.toir.service.ConditionReadingService;

import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.dto.conditionreading.ConditionReadingDto;
import com.toir.dto.conditionreading.ConditionReadingRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "condition-readings")
public class ConditionReadingController {

    private final ConditionReadingService service;
    private final SecurityScope securityScope;

    public ConditionReadingController(ConditionReadingService service, SecurityScope securityScope) {
        this.service = service;
        this.securityScope = securityScope;
    }

    @GetMapping("/equipment/{equipmentId}/condition-readings")
    public List<ConditionReadingDto> list(@PathVariable UUID equipmentId,
                                          @RequestParam(required = false) ConditionParameter parameter) {
        return service.findForEquipment(equipmentId, parameter);
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
    public List<ConditionReadingDto> alarms() {
        return service.findAlarms();
    }
}
