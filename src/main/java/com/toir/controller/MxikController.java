package com.toir.controller;

import com.toir.dto.mxik.MxikDto;
import com.toir.dto.mxik.MxikNameCodeCountProjection;
import com.toir.dto.mxik.MxikNameCountProjection;
import com.toir.dto.mxik.MxikRequest;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.MxikService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mxik")
@Tag(name = "mxik")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class MxikController {

    private final MxikService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<Page<MxikDto>> list(
            @RequestParam(required = false, name = "searchParam") String searchParam,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.findAll(searchParam, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<MxikDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_CREATE')")
    public ResponseEntity<MxikDto> create(@Valid @RequestBody MxikRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_UPDATE')")
    public ResponseEntity<MxikDto> update(@PathVariable UUID id, @Valid @RequestBody MxikRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/delete/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/catalog/groups")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<List<MxikNameCountProjection>> groups() {
        return ResponseEntity.ok(service.listGroupCounts());
    }

    @GetMapping("/catalog/classes")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<List<MxikNameCountProjection>> classes(@RequestParam String groupName) {
        return ResponseEntity.ok(service.listClassCounts(groupName));
    }

    @GetMapping("/catalog/positions")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<List<MxikNameCountProjection>> positions(@RequestParam String className) {
        return ResponseEntity.ok(service.listPositionCounts(className));
    }

    @GetMapping("/catalog/sub-positions")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<List<MxikNameCountProjection>> subPositions(@RequestParam String positionName) {
        return ResponseEntity.ok(service.listSubPositionCounts(positionName));
    }

    @GetMapping("/catalog/mxiks")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<List<MxikNameCodeCountProjection>> mxiks(@RequestParam String subPositionName) {
        return ResponseEntity.ok(service.listMxikCounts(subPositionName));
    }
}
