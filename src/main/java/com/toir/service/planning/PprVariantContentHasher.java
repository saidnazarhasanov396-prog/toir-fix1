package com.toir.service.planning;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationRequest;
import com.toir.entity.planning.PprPlanningVariantItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PprVariantContentHasher {

    public static final int VERSION = 1;

    public String compute(
            MaintenanceScheduleCalculationRequest request,
            List<PprPlanningVariantItem> items) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(items, "items");

        StringBuilder canonical = new StringBuilder("ppr-variant:v1\n");
        append(canonical, request.name());
        append(canonical, request.notes());
        append(canonical, request.fromDate());
        append(canonical, request.toDate());
        append(canonical, request.scopeType());
        appendSortedIds(canonical, request.equipmentIds());
        appendSortedIds(canonical, request.equipmentTypeIds());
        append(canonical, request.departmentId());
        append(canonical, request.anchorMode());
        append(canonical, request.shiftFromExcludedWeekdays());
        append(canonical, request.recurrenceAnchor());
        if (request.excludedWeekdays() == null) {
            append(canonical, null);
        } else {
            request.excludedWeekdays().stream()
                    .map(Enum::name)
                    .sorted()
                    .forEach(value -> append(canonical, value));
        }

        items.stream()
                .sorted(Comparator.comparing(PprPlanningVariantItem::getSourceItemKey))
                .forEach(item -> {
                    append(canonical, item.getSourceItemKey());
                    append(canonical, item.getEquipmentId());
                    append(canonical, item.getRegulationId());
                    append(canonical, item.getMaintenanceRuleId());
                    append(canonical, item.getTemplateId());
                    append(canonical, item.getMaintenanceType());
                    append(canonical, item.getTriggerType());
                    append(canonical, item.getTriggerDiscriminator());
                    append(canonical, item.getCycleOrdinal());
                    append(canonical, item.getPlannedDate());
                    append(canonical, item.getScheduledStart());
                    append(canonical, item.getScheduledEnd());
                    append(canonical, item.getDueDate());
                    append(canonical, item.getNormativeLaborHours());
                    append(canonical, item.getPriority());
                    append(canonical, item.getDepartmentId());
                    append(canonical, item.getTaskTitleSnapshot());
                    append(canonical, item.getWorkOrderLeadDays());
                    item.getRequiredEvidenceTypes().stream()
                            .map(Enum::name)
                            .sorted()
                            .forEach(value -> append(canonical, value));
                });

        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void appendSortedIds(StringBuilder target, List<UUID> values) {
        if (values == null) {
            append(target, null);
            return;
        }
        values.stream()
                .map(UUID::toString)
                .sorted()
                .forEach(value -> append(target, value));
    }

    private static void append(StringBuilder target, Object value) {
        String normalized = value == null ? "<null>" : value.toString().trim();
        target.append(normalized.length()).append(':').append(normalized).append('\n');
    }
}
