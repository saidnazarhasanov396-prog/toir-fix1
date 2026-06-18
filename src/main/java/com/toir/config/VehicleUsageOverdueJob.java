package com.toir.config;

import com.toir.service.VehicleUsageOverdueNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VehicleUsageOverdueJob {

    private final VehicleUsageOverdueNotificationService notificationService;

    @Scheduled(fixedDelayString = "${toir.vehicle-usage-overdue.scheduler.fixed-delay-ms:300000}")
    public void run() {
        try {
            int notified = notificationService.notifyOverdueSessions();
            if (notified > 0) {
                log.info("Vehicle usage overdue job completed: notified={}", notified);
            }
        } catch (Exception ex) {
            log.error("Vehicle usage overdue job failed", ex);
        }
    }
}
