package com.toir.config;

import com.toir.service.sparepartlifecycle.SparePartLifecycleEvaluationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SparePartLifecycleCalendarJobTest {

    @Test
    void scheduledScanReevaluatesAllActiveInstallations() {
        SparePartLifecycleEvaluationService service = mock(SparePartLifecycleEvaluationService.class);
        when(service.reevaluateAllActive(any(Instant.class))).thenReturn(List.of());

        new SparePartLifecycleCalendarJob(service).scan();

        verify(service).reevaluateAllActive(any(Instant.class));
    }
}
