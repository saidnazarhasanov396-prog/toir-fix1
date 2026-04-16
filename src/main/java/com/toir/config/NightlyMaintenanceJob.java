package com.toir.config;

import com.toir.service.CertificationService;
import com.toir.service.OverdueDetectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ночной пакет обслуживания: пересчёт просрочки (ППР/заявки/наряды/поверки/
 * сертификаты) и автоперевод ACTIVE→EXPIRED. Запускается каждый день в 03:00.
 */
@Component
public class NightlyMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(NightlyMaintenanceJob.class);

    private final OverdueDetectorService overdueDetectorService;
    private final CertificationService certificationService;

    public NightlyMaintenanceJob(OverdueDetectorService overdueDetectorService,
                                 CertificationService certificationService) {
        this.overdueDetectorService = overdueDetectorService;
        this.certificationService = certificationService;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void runNightly() {
        try {
            OverdueDetectorService.EvaluationResult r = overdueDetectorService.evaluate();
            log.info("Nightly overdue detector: ppr={}, requests={}, workOrders={}, calibrations={}, certs={}, notifications={}",
                    r.pprMarkedOverdue(), r.repairRequestBreaches(), r.workOrderBreaches(),
                    r.calibrationBreaches(), r.certificationExpired(), r.notificationsCreated());
        } catch (Exception e) {
            log.error("Nightly overdue detector failed", e);
        }
        try {
            int expired = certificationService.markExpired();
            log.info("Nightly cert expiry: {} certifications marked EXPIRED", expired);
        } catch (Exception e) {
            log.error("Nightly cert expiry failed", e);
        }
    }
}
