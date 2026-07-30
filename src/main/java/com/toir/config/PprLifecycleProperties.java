package com.toir.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.ZoneId;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "toir.ppr-lifecycle")
public class PprLifecycleProperties {

    private boolean planningSessionsEnabled;
    private boolean dueWorkOrderGenerationEnabled;
    private boolean strictClosureEnabled;

    @Valid
    private WorkOrderGeneration workOrderGeneration = new WorkOrderGeneration();

    @Getter
    @Setter
    public static class WorkOrderGeneration {

        @NotBlank
        private String cron = "0 10 * * * *";

        private ZoneId timezone = ZoneId.of("Asia/Tashkent");

        @Min(1)
        @Max(10_000)
        private int batchSize = 100;

        private UUID actorId;
    }
}
