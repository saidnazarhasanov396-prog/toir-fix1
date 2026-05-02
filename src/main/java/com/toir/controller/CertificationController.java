package com.toir.controller;
import com.toir.dto.certification.CertificationTypeDto;
import com.toir.dto.certification.UserCertificationDto;
import com.toir.dto.certification.UserCertificationRequest;
import com.toir.service.CertificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "certifications")
public class CertificationController {

    private final CertificationService service;

    public CertificationController(CertificationService service) {
        this.service = service;
    }

    @GetMapping("/certification-types")
    public ResponseEntity<List<CertificationTypeDto>> listTypes(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.findTypes(code,name,search));
    }

    @PostMapping("/certification-types")
    public ResponseEntity<CertificationTypeDto> createType(@Valid @RequestBody CertificationTypeDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createType(r));
    }

    @PutMapping("/certification-types/{id}")
    public ResponseEntity<CertificationTypeDto> updateType(@PathVariable UUID id, @Valid @RequestBody CertificationTypeDto r) {
        return ResponseEntity.ok(service.updateType(id, r));
    }

    @DeleteMapping("/certification-types/{id}")
    public ResponseEntity<Void> deleteType(@PathVariable UUID id) {
        service.deleteType(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user-certifications")
    public ResponseEntity<List<UserCertificationDto>> list(
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.findAll(search));
    }

    @GetMapping("/user-certification/{id}")
    public ResponseEntity<UserCertificationDto> findOne(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findOne(id));
    }

    @GetMapping("/user-certifications/expiring")
    public ResponseEntity<List<UserCertificationDto>> expiring(@RequestParam(defaultValue = "30") int withinDays) {
        return ResponseEntity.ok(service.findExpiring(withinDays));
    }

    @PostMapping("/user-certifications")
    public ResponseEntity<UserCertificationDto> issue(@Valid @RequestBody UserCertificationRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issue(r));
    }

    @PostMapping("/user-certifications/{id}/suspend")
    public ResponseEntity<UserCertificationDto> suspend(@PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(service.suspend(id, reason));
    }

    @PostMapping("/user-certifications/{id}/revoke")
    public ResponseEntity<UserCertificationDto> revoke(@PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(service.revoke(id, reason));
    }

    @PostMapping("/user-certifications/recompute-expired")
    public ResponseEntity<Integer> recompute() { return ResponseEntity.ok(service.markExpired()); }
}
