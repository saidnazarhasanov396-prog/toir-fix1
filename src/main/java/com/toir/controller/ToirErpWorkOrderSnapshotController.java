package com.toir.controller;

import com.toir.security.RequiresAdmin;
import com.toir.service.integration.ToirErpWorkOrderSnapshotPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Explicit owner baseline export; it does not alter the TOIR work-order state machine. */
@RestController
@RequestMapping("/api/v1/integrations/erp/work-order-snapshots")
@RequiresAdmin
@RequiredArgsConstructor
public class ToirErpWorkOrderSnapshotController {
    private final ToirErpWorkOrderSnapshotPublisher publisher;

    @PostMapping
    public ToirErpWorkOrderSnapshotPublisher.SnapshotQueueResult queue(@RequestParam(required = false) Integer chunkSize) {
        return publisher.queueForErp(chunkSize);
    }

    @PostMapping("/work-orders/{workOrderId}/export")
    public void queueDelta(@PathVariable java.util.UUID workOrderId) {
        publisher.queueDelta(workOrderId);
    }
}
