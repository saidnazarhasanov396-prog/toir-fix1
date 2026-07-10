package com.toir.service.sparepartlifecycle;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SparePartSlotNormalizer {

    public String normalizeNullable(String slotCode) {
        if (slotCode == null || slotCode.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(slotCode.trim(), Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? null : normalized;
    }

    public String normalizeRequired(String slotCode) {
        String normalized = normalizeNullable(slotCode);
        return normalized == null ? "DEFAULT" : normalized;
    }

    public String positionKey(UUID equipmentNodeId, String slotCode) {
        String nodeKey = equipmentNodeId == null ? "ROOT" : equipmentNodeId.toString();
        return "N:" + nodeKey + ":S:" + normalizeRequired(slotCode);
    }
}
