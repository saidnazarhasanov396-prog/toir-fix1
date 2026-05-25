package com.toir.controller;

import com.toir.dto.file.PresignedUrlResponse;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleStatsResponse;
import com.toir.dto.vehicle.VehicleSummaryDto;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.SecurityScope;
import com.toir.service.VehicleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService service;
    private final SecurityScope securityScope;

    @GetMapping
    public ResponseEntity<Page<VehicleSummaryDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.list(
                securityScope.enforceDepartmentScope(departmentId),
                status,
                search,
                page,
                size
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<VehicleStatsResponse> stats(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.getStats(
                securityScope.enforceDepartmentScope(departmentId),
                search
        ));
    }

    @GetMapping("/{equipmentId}")
    public ResponseEntity<VehicleDetailDto> get(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.findByEquipmentId(equipmentId));
    }

    @PostMapping
    public ResponseEntity<VehicleDetailDto> create(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{equipmentId}")
    public ResponseEntity<VehicleDetailDto> update(@PathVariable UUID equipmentId, @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(service.update(equipmentId, request));
    }

    @PostMapping(value = "/{equipmentId}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VehicleDetailDto> attachDocument(
            @PathVariable UUID equipmentId,
            @RequestParam("document") MultipartFile document,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.attachDocument(equipmentId, document, currentUserId(user)));
    }

    @GetMapping("/{equipmentId}/document")
    public ResponseEntity<VehicleDetailDto.DocumentRef> getDocument(
            @PathVariable UUID equipmentId,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.getDocument(equipmentId, currentUserId(user)));
    }

    @GetMapping("/{equipmentId}/document/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getDocumentPresignedUrl(
            @PathVariable UUID equipmentId,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.getDocumentPresignedUrl(equipmentId, currentUserId(user)));
    }

    @DeleteMapping("/{equipmentId}/document")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID equipmentId,
            @CurrentUser AuthenticatedUser user
    ) {
        service.deleteDocument(equipmentId, currentUserId(user));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID equipmentId) {
        service.delete(equipmentId);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(AuthenticatedUser user) {
        if (user == null || user.id() == null || user.id().isBlank()) {
            throw RestException.unauthorized("Authenticated user is required");
        }
        return UUID.fromString(user.id());
    }
}
