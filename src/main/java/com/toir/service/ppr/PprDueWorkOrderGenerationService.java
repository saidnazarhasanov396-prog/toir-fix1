package com.toir.service.ppr;

import com.toir.config.PprLifecycleProperties;
import com.toir.entity.PprTask;
import com.toir.repository.PprTaskRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PprDueWorkOrderGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PprDueWorkOrderGenerationService.class);

    private final PprTaskRepository tasks;
    private final PprDueWorkOrderGenerationItemService itemService;
    private final PprLifecycleProperties properties;

    public GenerationRunResult generateDue(Instant now) {
        if (!properties.isDueWorkOrderGenerationEnabled()) {
            return new GenerationRunResult(0, List.of());
        }
        UUID actorId = properties.getWorkOrderGeneration().getActorId();
        if (actorId == null) {
            throw new IllegalStateException(
                    "toir.ppr-lifecycle.work-order-generation.actor-id is required when due generation is enabled");
        }
        ZoneId zone = properties.getWorkOrderGeneration().getTimezone();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, zone);
        List<PprTask> candidates = tasks.findDueForWorkOrderGeneration(
                localNow, properties.getWorkOrderGeneration().getBatchSize());
        List<SkippedItem> skipped = new ArrayList<>();
        int created = 0;
        for (PprTask task : candidates) {
            try {
                String skipReason = itemService.generateOne(task, localNow, zone, actorId);
                if (skipReason == null) {
                    created++;
                } else {
                    skipped.add(new SkippedItem(task.getId(), skipReason));
                }
            } catch (RuntimeException exception) {
                log.error("Failed to generate PPR work order for task {}", task.getId(), exception);
                skipped.add(new SkippedItem(task.getId(), "GENERATION_FAILED"));
            }
        }
        return new GenerationRunResult(created, List.copyOf(skipped));
    }

    public record GenerationRunResult(int createdCount, List<SkippedItem> skipped) {
    }

    public record SkippedItem(UUID taskId, String reason) {
    }
}
