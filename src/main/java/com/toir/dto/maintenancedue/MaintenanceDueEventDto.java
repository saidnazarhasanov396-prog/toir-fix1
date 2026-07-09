package com.toir.dto.maintenancedue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.maintenanceplanning.MaintenanceDueStructuredExplanationDto;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterType;
import com.toir.service.maintanance.MaintenanceDueReasonI18n;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MaintenanceDueEventDto(
        UUID id,
        UUID equipmentId,
        UUID regulationId,
        UUID equipmentMaintenanceRuleId,
        UUID templateId,
        MaintenanceDueEventStatus status,
        MaintenanceDueStatus dueStatus,
        MaintenanceTriggerSource triggerSource,
        String cycleKey,
        Instant dueAt,
        MeterType meterType,
        Double meterCurrentValue,
        Double meterAnchorValue,
        Double meterInterval,
        Double meterRemaining,
        UUID createdTaskId,
        UUID createdWorkOrderId,
        Instant detectedAt,
        Instant resolvedAt,
        String resolutionReason,
        String explanation,
        MaintenanceDueStructuredExplanationDto structuredExplanation,
        Ref equipment,
        Ref regulation
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, String> METER_REASON_CODE_BY_STATUS = Map.of(
            "OVERDUE", "METER_OVERDUE",
            "DUE", "METER_DUE"
    );

    public record Ref(UUID id, String code, String name) {}

    public MaintenanceDueEventDto(
            UUID id,
            UUID equipmentId,
            UUID regulationId,
            UUID equipmentMaintenanceRuleId,
            UUID templateId,
            MaintenanceDueEventStatus status,
            MaintenanceDueStatus dueStatus,
            MaintenanceTriggerSource triggerSource,
            String cycleKey,
            Instant dueAt,
            MeterType meterType,
            Double meterCurrentValue,
            Double meterAnchorValue,
            Double meterInterval,
            Double meterRemaining,
            UUID createdTaskId,
            UUID createdWorkOrderId,
            Instant detectedAt,
            Instant resolvedAt,
            String resolutionReason,
            String explanation,
            Ref equipment,
            Ref regulation
    ) {
        this(id, equipmentId, regulationId, equipmentMaintenanceRuleId, templateId, status, dueStatus, triggerSource,
                cycleKey, dueAt, meterType, meterCurrentValue, meterAnchorValue, meterInterval, meterRemaining,
                createdTaskId, createdWorkOrderId, detectedAt, resolvedAt, resolutionReason, explanation,
                null, equipment, regulation);
    }

    public static MaintenanceDueEventDto from(MaintenanceDueEvent event, Ref equipment, Ref regulation) {
        return from(event, equipment, regulation, null);
    }

    public static MaintenanceDueEventDto from(MaintenanceDueEvent event, Ref equipment, Ref regulation, String lang) {
        MaintenanceDueStatus dueStatus = normalizedDueStatus(event);
        String reasonCode = normalizedReasonCode(event, dueStatus);
        String normalizedLang = MaintenanceDueReasonI18n.normalizeLang(lang);
        Map<String, Object> reasonParams = parseReasonParams(event.getReasonParams());
        String explanation = normalizedLang != null && reasonCode != null
                ? MaintenanceDueReasonI18n.renderFullByName(reasonCode, reasonParams, List.of(), List.of(), normalizedLang)
                : normalizedExplanation(event, dueStatus);
        return new MaintenanceDueEventDto(
                event.getId(),
                event.getEquipmentId(),
                event.getRegulationId(),
                event.getEquipmentMaintenanceRuleId(),
                event.getTemplateId(),
                event.getStatus(),
                dueStatus,
                event.getTriggerSource(),
                event.getCycleKey(),
                event.getDueAt(),
                event.getMeterType(),
                event.getMeterCurrentValue(),
                event.getMeterAnchorValue(),
                event.getMeterInterval(),
                event.getMeterRemaining(),
                event.getCreatedTaskId(),
                event.getCreatedWorkOrderId(),
                event.getDetectedAt(),
                event.getResolvedAt(),
                event.getResolutionReason(),
                explanation,
                structuredExplanation(event, dueStatus, explanation, reasonCode, reasonParams),
                equipment,
                regulation
        );
    }

    private static MaintenanceDueStatus normalizedDueStatus(MaintenanceDueEvent event) {
        if (!meterDominant(event)) {
            return event.getDueStatus();
        }
        int remaining = BigDecimal.valueOf(event.getMeterRemaining()).compareTo(BigDecimal.ZERO);
        if (remaining < 0) {
            return MaintenanceDueStatus.OVERDUE;
        }
        if (remaining == 0) {
            return MaintenanceDueStatus.DUE;
        }
        return event.getDueStatus();
    }

    private static String normalizedExplanation(MaintenanceDueEvent event, MaintenanceDueStatus dueStatus) {
        if (!meterDominant(event)) {
            return event.getExplanation();
        }
        if (event.getReasonCode() != null) {
            // Structured events already carry the right text for their persisted reason code; the
            // meter-remaining crossing zero is reflected via normalizedReasonCode()/normalizedDueStatus().
            return event.getExplanation();
        }
        if (dueStatus == MaintenanceDueStatus.OVERDUE) {
            return replaceMeterExplanation(event.getExplanation(), "Meter trigger overdue");
        }
        if (dueStatus == MaintenanceDueStatus.DUE) {
            return replaceMeterExplanation(event.getExplanation(), "Meter trigger due");
        }
        return event.getExplanation();
    }

    private static String normalizedReasonCode(MaintenanceDueEvent event, MaintenanceDueStatus dueStatus) {
        if (!meterDominant(event)) {
            return event.getReasonCode();
        }
        String override = METER_REASON_CODE_BY_STATUS.get(dueStatus == null ? null : dueStatus.name());
        return override != null ? override : event.getReasonCode();
    }

    private static boolean meterDominant(MaintenanceDueEvent event) {
        if (event.getMeterType() == null || event.getMeterRemaining() == null) {
            return false;
        }
        if (event.getTriggerSource() == MaintenanceTriggerSource.METER_READING) {
            return true;
        }
        if (event.getReasonCode() != null) {
            return isMeterReasonCode(event.getReasonCode());
        }
        // Legacy events detected before reason codes were persisted - fall back to the free-text heuristic.
        String explanation = event.getExplanation();
        return explanation == null || explanation.isBlank() || explanation.startsWith("Meter trigger");
    }

    private static boolean isMeterReasonCode(String reasonCode) {
        return switch (reasonCode) {
            case "METER_OVERDUE", "METER_DUE", "METER_UPCOMING", "METER_NOT_DUE" -> true;
            default -> false;
        };
    }

    private static String replaceMeterExplanation(String explanation, String replacement) {
        if (explanation == null || explanation.isBlank()) {
            return replacement;
        }
        return explanation
                .replace("Meter trigger overdue", replacement)
                .replace("Meter trigger upcoming", replacement)
                .replace("Meter trigger not due", replacement)
                .replace("Meter trigger due", replacement);
    }

    private static MaintenanceDueStructuredExplanationDto structuredExplanation(
            MaintenanceDueEvent event,
            MaintenanceDueStatus dueStatus,
            String explanation,
            String reasonCode,
            Map<String, Object> reasonParams
    ) {
        String blockingCode = null;
        String blockingField = null;
        String fixLink = null;
        if (dueStatus == MaintenanceDueStatus.BLOCKED && event.getMeterType() != null) {
            blockingCode = "MISSING_ACTIVE_METER";
            blockingField = event.getMeterType().name();
            fixLink = "/equipment/%s/meters".formatted(event.getEquipmentId());
        } else if (dueStatus == MaintenanceDueStatus.BLOCKED) {
            blockingCode = "MISSING_COMPLETION_ANCHOR";
            blockingField = "completionAnchor";
            fixLink = "/equipment/%s/maintenance".formatted(event.getEquipmentId());
        }
        return new MaintenanceDueStructuredExplanationDto(
                event.getMeterAnchorValue() == null ? null : "COMPLETION_ANCHOR",
                null,
                null,
                event.getMeterType(),
                event.getMeterCurrentValue(),
                event.getMeterInterval(),
                event.getMeterRemaining(),
                null,
                null,
                null,
                null,
                explanation,
                blockingCode,
                blockingField,
                fixLink,
                reasonCode,
                reasonCode == null ? null : "maintenanceDue.reasons." + reasonCode,
                reasonParams,
                List.of(),
                List.of()
        );
    }

    private static Map<String, Object> parseReasonParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
