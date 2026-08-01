package com.toir.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.service.ppr.PprDueWorkOrderGenerationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PprDueWorkOrderGenerationJobTest {

    @Mock PprDueWorkOrderGenerationService generationService;

    @Test
    void scheduledTickDelegatesToDueGenerationService() {
        when(generationService.generateDue(any(Instant.class)))
                .thenReturn(new PprDueWorkOrderGenerationService.GenerationRunResult(1, List.of()));

        new PprDueWorkOrderGenerationJob(generationService).generateDueWorkOrders();

        verify(generationService).generateDue(any(Instant.class));
    }
}
