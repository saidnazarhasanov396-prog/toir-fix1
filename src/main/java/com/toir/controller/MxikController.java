package com.toir.controller;

import com.toir.dto.mxik.MxikDto;
import com.toir.dto.mxik.MxikNameCodeCountProjection;
import com.toir.dto.mxik.MxikNameCountProjection;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.MxikService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(service.findAll(search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<MxikDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/catalog/groups")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<List<MxikNameCountProjection>> groups() {
        return ResponseEntity.ok(service.listGroupCounts());
    }

    @GetMapping("/catalog/sub-positions")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<List<MxikNameCountProjection>> subPositions(@RequestParam String groupName) {
        return ResponseEntity.ok(service.listSubPositionCounts(groupName));
    }

    @GetMapping("/catalog/mxiks")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_READ')")
    public ResponseEntity<Page<MxikNameCodeCountProjection>> mxiks(
            @RequestParam String positionName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(service.listMxikCounts(positionName, page, size));
    }
}
