package com.toir.controller;
import com.toir.dto.mobilesync.MobileSyncRequest;
import com.toir.dto.mobilesync.MobileSyncResult;
import com.toir.service.MobileSyncService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mobile/sync")
@Tag(name = "mobile-sync")
public class MobileSyncController {

    private final MobileSyncService service;

    public MobileSyncController(MobileSyncService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MobileSyncResult> sync(@RequestBody MobileSyncRequest request) {
        return ResponseEntity.ok(service.sync(request));
    }
}
