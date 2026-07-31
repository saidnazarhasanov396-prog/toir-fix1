package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextPolicy;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class EquipmentLifecycleExportProfileResolver {
    public static final String STANDARD_V1 = "standard-v1";

    private final EquipmentLifecycleExportProperties properties;
    private final ObjectMapper objectMapper;
    private final EquipmentLifecycleExportFingerprintService fingerprintService;

    public EquipmentLifecycleExportProfileResolver(
            EquipmentLifecycleExportProperties properties,
            ObjectMapper objectMapper,
            EquipmentLifecycleExportFingerprintService fingerprintService
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.fingerprintService = fingerprintService;
    }

    public ResolvedProfile resolve(String profileName, Instant asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("asOf is required");
        }
        if (!STANDARD_V1.equals(profileName) || !properties.getAllowedProfiles().contains(profileName)) {
            throw new IllegalArgumentException("Equipment lifecycle export profile is not allowlisted");
        }
        EquipmentLifecycleExportProperties.StandardProfile configured = properties.getStandardProfile();
        if (configured.getHistoryLookback() == null || configured.getHistoryLookback().isNegative()
                || configured.getFuturePlanningHorizon() == null || configured.getFuturePlanningHorizon().isNegative()
                || configured.getSectionLimit() <= 0) {
            throw new IllegalStateException("standard-v1 export profile must be fully bounded");
        }
        Set<EquipmentLifecycleSection> sections = EnumSet.allOf(EquipmentLifecycleSection.class);
        Map<EquipmentLifecycleSection, Integer> limits = new EnumMap<>(EquipmentLifecycleSection.class);
        sections.forEach(section -> limits.put(section, configured.getSectionLimit()));
        PolicySnapshot snapshot = new PolicySnapshot(
                asOf.minus(configured.getHistoryLookback()),
                configured.getFuturePlanningHorizon(),
                sections,
                limits,
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW
        );
        String policyJson = write(snapshot);
        return new ResolvedProfile(profileName, snapshot.toPolicy(), policyJson,
                fingerprintService.sha256(policyJson));
    }

    public EquipmentLifecycleContextPolicy restore(String policyJson) {
        if (policyJson == null || policyJson.isBlank()) {
            throw new IllegalArgumentException("Persisted resolved policy is required");
        }
        try {
            return objectMapper.readValue(policyJson, PolicySnapshot.class).toPolicy();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted export policy is invalid", exception);
        }
    }

    private String write(PolicySnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not persist resolved export policy", exception);
        }
    }

    public record ResolvedProfile(
            String profileName,
            EquipmentLifecycleContextPolicy policy,
            String policyJson,
            String policyFingerprint
    ) {}

    public record PolicySnapshot(
            Instant historyStart,
            Duration futurePlanningHorizon,
            Set<EquipmentLifecycleSection> includedSections,
            Map<EquipmentLifecycleSection, Integer> maxRowsBySection,
            EquipmentLifecycleContextPolicy.MeasurementGranularity measurementGranularity
    ) {
        public EquipmentLifecycleContextPolicy toPolicy() {
            return new EquipmentLifecycleContextPolicy(
                    historyStart,
                    futurePlanningHorizon,
                    includedSections,
                    maxRowsBySection,
                    measurementGranularity
            );
        }
    }
}
