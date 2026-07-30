package com.toir.config;

import com.toir.service.ppr.PprDueWorkOrderGenerationService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PprDueWorkOrderGenerationJob {

    private final PprDueWorkOrderGenerationService generationService;

    @Scheduled(
            cron = "${toir.ppr-lifecycle.work-order-generation.cron:0 10 * * * *}",
            zone = "${toir.ppr-lifecycle.work-order-generation.timezone:Asia/Tashkent}")
    public void generateDueWorkOrders() {
        try {
            PprDueWorkOrderGenerationService.GenerationRunResult result =
                    generationService.generateDue(Instant.now());
            if (result.createdCount() > 0 || !result.skipped().isEmpty()) {
                log.info(
                        "PPR due work-order generation completed: created={}, skipped={}",
                        result.createdCount(),
                        result.skipped().size());
            }
        } catch (RuntimeException exception) {
            log.error("PPR due work-order generation failed", exception);
        }
    }
}
