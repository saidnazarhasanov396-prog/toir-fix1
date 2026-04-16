package com.toir.controller;
import com.toir.service.CertificationService;

import com.toir.dto.certification.CertificationTypeDto;
import com.toir.dto.certification.UserCertificationDto;
import com.toir.dto.certification.UserCertificationRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "certifications")
public class CertificationController {

    private final CertificationService service;

    public CertificationController(CertificationService service) {
        this.service = service;
    }

    @GetMapping("/certification-types")
    public List<CertificationTypeDto> listTypes() { return service.findTypes(); }

    @PostMapping("/certification-types")
    public ResponseEntity<CertificationTypeDto> createType(@Valid @RequestBody CertificationTypeDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createType(r));
    }

    @PutMapping("/certification-types/{id}")
    public CertificationTypeDto updateType(@PathVariable UUID id, @Valid @RequestBody CertificationTypeDto r) {
        return service.updateType(id, r);
    }

    @DeleteMapping("/certification-types/{id}")
    public ResponseEntity<Void> deleteType(@PathVariable UUID id) {
        service.deleteType(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user-certifications")
    public List<UserCertificationDto> list(@RequestParam(required = false) UUID userId) {
        return userId != null ? service.findForUser(userId) : service.findAll();
    }

    @GetMapping("/user-certifications/expiring")
    public List<UserCertificationDto> expiring(@RequestParam(defaultValue = "30") int withinDays) {
        return service.findExpiring(withinDays);
    }

    @PostMapping("/user-certifications")
    public ResponseEntity<UserCertificationDto> issue(@Valid @RequestBody UserCertificationRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issue(r));
    }

    @PostMapping("/user-certifications/{id}/suspend")
    public UserCertificationDto suspend(@PathVariable UUID id, @RequestParam String reason) {
        return service.suspend(id, reason);
    }

    @PostMapping("/user-certifications/{id}/revoke")
    public UserCertificationDto revoke(@PathVariable UUID id, @RequestParam String reason) {
        return service.revoke(id, reason);
    }

    @PostMapping("/user-certifications/recompute-expired")
    public int recompute() { return service.markExpired(); }
}
