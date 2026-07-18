package com.toir.controller;

import com.toir.security.RequiresAdmin;
import com.toir.service.integration.ToirErpEquipmentSnapshotPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Explicit staged-cutover command; ongoing status transitions remain event driven. */
@RestController
@RequestMapping("/api/v1/integrations/erp/equipment-snapshots")
@RequiresAdmin
@RequiredArgsConstructor
public class ToirErpEquipmentSnapshotController {
    private final ToirErpEquipmentSnapshotPublisher publisher;

    @PostMapping
    public ToirErpEquipmentSnapshotPublisher.SnapshotQueueResult queue(
        @RequestParam(required = false) Integer chunkSize
    ) {
        return publisher.queueForErp(chunkSize);
    }
}
