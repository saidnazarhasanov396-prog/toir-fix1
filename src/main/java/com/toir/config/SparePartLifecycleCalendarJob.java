package com.toir.config;

import com.toir.service.sparepartlifecycle.SparePartLifecycleEvaluationService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SparePartLifecycleCalendarJob {

    private final SparePartLifecycleEvaluationService evaluationService;

    @Scheduled(cron = "${toir.spare-part-lifecycle.calendar-scan-cron:0 15 3 * * *}", zone = "UTC")
    public void scan() {
        Instant evaluatedAt = Instant.now();
        try {
            int evaluated = evaluationService.reevaluateAllActive(evaluatedAt).size();
            log.info("Spare-part lifecycle calendar scan evaluated {} active installations", evaluated);
        } catch (RuntimeException exception) {
            log.error("Spare-part lifecycle calendar scan failed", exception);
        }
    }
}
