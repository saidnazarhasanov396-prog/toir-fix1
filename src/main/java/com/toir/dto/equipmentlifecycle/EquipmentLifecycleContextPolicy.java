package com.toir.dto.equipmentlifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record EquipmentLifecycleContextPolicy(
        Instant historyStart,
        Duration futurePlanningHorizon,
        Set<EquipmentLifecycleSection> includedSections,
        Map<EquipmentLifecycleSection, Integer> maxRowsBySection,
        MeasurementGranularity measurementGranularity
) {
    public EquipmentLifecycleContextPolicy {
        if (historyStart == null) {
            throw new IllegalArgumentException("historyStart is required");
        }
        if (futurePlanningHorizon == null || futurePlanningHorizon.isNegative()) {
            throw new IllegalArgumentException("futurePlanningHorizon must be zero or positive");
        }
        if (includedSections == null) {
            throw new IllegalArgumentException("includedSections is required");
        }
        if (maxRowsBySection == null) {
            throw new IllegalArgumentException("maxRowsBySection is required");
        }
        if (measurementGranularity == null) {
            throw new IllegalArgumentException("measurementGranularity is required");
        }
        includedSections = Set.copyOf(includedSections);
        maxRowsBySection = Map.copyOf(maxRowsBySection);
        for (EquipmentLifecycleSection section : includedSections) {
            if (section.requiresLimit()) {
                Integer limit = maxRowsBySection.get(section);
                if (limit == null) {
                    throw new IllegalArgumentException("Explicit limit is required for " + section);
                }
                if (limit <= 0) {
                    throw new IllegalArgumentException("Limit must be positive for " + section);
                }
            }
        }
    }

    public boolean includes(EquipmentLifecycleSection section) {
        return includedSections.contains(section);
    }

    public int requiredLimit(EquipmentLifecycleSection section) {
        if (!includes(section)) {
            throw new IllegalArgumentException("Section is not included: " + section);
        }
        Integer limit = maxRowsBySection.get(section);
        if (limit == null || limit <= 0) {
            throw new IllegalArgumentException("Explicit positive limit is required for " + section);
        }
        return limit;
    }

    public Instant planningEnd(Instant asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("asOf is required");
        }
        return asOf.plus(futurePlanningHorizon);
    }

    public enum MeasurementGranularity {
        RAW
    }
}
