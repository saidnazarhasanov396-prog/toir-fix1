package com.toir.dto.i18n;

import java.util.Map;

public record I18nBundleResponse(
        Map<String, String> requestStatus,
        Map<String, String> priority,
        Map<String, String> criticality,
        Map<String, String> workOrderStatus,
        Map<String, String> maintenanceKind,
        Map<String, String> severity,
        Map<String, String> procurementStatus
) {
    public static I18nBundleResponse from(Map<String, Map<String, String>> bundle) {
        return new I18nBundleResponse(
                bundle.getOrDefault("requestStatus", Map.of()),
                bundle.getOrDefault("priority", Map.of()),
                bundle.getOrDefault("criticality", Map.of()),
                bundle.getOrDefault("workOrderStatus", Map.of()),
                bundle.getOrDefault("maintenanceKind", Map.of()),
                bundle.getOrDefault("severity", Map.of()),
                bundle.getOrDefault("procurementStatus", Map.of())
        );
    }
}
