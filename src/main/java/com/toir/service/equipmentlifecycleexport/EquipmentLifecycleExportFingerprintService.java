package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class EquipmentLifecycleExportFingerprintService {
    private final ObjectMapper objectMapper;

    public EquipmentLifecycleExportFingerprintService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NormalizedRequest normalizeRequest(
            String profile,
            EquipmentLifecycleExportScopeMode scopeMode,
            List<UUID> equipmentIds
    ) {
        if (profile == null || profile.isBlank() || scopeMode == null) {
            throw new IllegalArgumentException("Export profile and scope mode are required");
        }
        List<UUID> normalizedIds = equipmentIds == null
                ? List.of()
                : equipmentIds.stream().filter(Objects::nonNull).distinct().sorted(Comparator.naturalOrder()).toList();
        if (scopeMode == EquipmentLifecycleExportScopeMode.ALL_AUTHORIZED && !normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("ALL_AUTHORIZED scope cannot include explicit Equipment IDs");
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("profile", profile.trim());
        root.put("scopeMode", scopeMode.name());
        ArrayNode ids = root.putArray("equipmentIds");
        normalizedIds.forEach(id -> ids.add(id.toString()));
        try {
            String json = objectMapper.writeValueAsString(root);
            return new NormalizedRequest(json, sha256(json), normalizedIds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not normalize export request", exception);
        }
    }

    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record NormalizedRequest(String json, String fingerprint, List<UUID> equipmentIds) {
        public NormalizedRequest {
            equipmentIds = List.copyOf(equipmentIds);
        }
    }
}
