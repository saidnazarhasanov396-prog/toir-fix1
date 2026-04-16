package com.toir.mobilesync;

import com.toir.conditionreading.dto.ConditionReadingRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Пакет офлайн-синхронизации с мобильного устройства: показания и результаты
 * обхода, собранные во время работы без связи. Каждый элемент имеет clientId
 * для идемпотентности повторных отправок.
 */
public record MobileSyncRequest(
        List<ReadingItem> readings,
        List<InspectionResultItem> inspectionResults,
        List<RoundCompletionItem> roundCompletions
) {
    public record ReadingItem(
            String clientId,
            UUID equipmentId,
            ConditionReadingRequest payload
    ) {}

    public record InspectionResultItem(
            String clientId,
            UUID roundId,
            UUID checkpointId,
            String status,
            Double measuredValue,
            String measuredUnit,
            String comment,
            Instant capturedAt
    ) {}

    public record RoundCompletionItem(
            String clientId,
            UUID roundId,
            String notes,
            Instant completedAt
    ) {}
}
