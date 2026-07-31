package com.toir.service.equipmentlifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextPolicy;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class EquipmentLifecycleFingerprintService {

    private final ObjectMapper canonicalMapper;

    public EquipmentLifecycleFingerprintService(ObjectMapper objectMapper) {
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String fingerprint(
            EquipmentLifecycleContextV1 context,
            EquipmentLifecycleContextPolicy policy
    ) {
        if (context == null || policy == null) {
            throw new IllegalArgumentException("context and policy are required");
        }
        FingerprintMaterial material = new FingerprintMaterial(
                context.schemaVersion(),
                context.asOf(),
                policyMaterial(policy),
                context.consistency(),
                context.equipment(),
                context.hierarchy(),
                context.technicalAttributes(),
                context.lifecycleHistory(),
                context.meterHistory(),
                context.maintenanceSummary(),
                context.maintenanceHistory(),
                context.plannedMaintenance(),
                context.workOrders(),
                context.defects(),
                context.repairRequests(),
                context.inspections(),
                context.conditionMeasurements(),
                context.installedComponents(),
                context.componentReplacementHistory(),
                context.warranty(),
                context.costSummary(),
                context.environmentContext(),
                context.documentMetadata(),
                context.dataQuality(),
                context.sourceWatermarks());
        try {
            byte[] canonicalJson = canonicalMapper.writeValueAsBytes(material);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonicalJson));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize equipment lifecycle fingerprint material", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private PolicyMaterial policyMaterial(EquipmentLifecycleContextPolicy policy) {
        List<String> sections = policy.includedSections().stream()
                .map(Enum::name)
                .sorted()
                .toList();
        Map<String, Integer> limits = new TreeMap<>();
        policy.includedSections().stream()
                .filter(section -> policy.maxRowsBySection().containsKey(section))
                .forEach(section -> limits.put(
                        section.name(), policy.maxRowsBySection().get(section)));
        return new PolicyMaterial(
                policy.historyStart(),
                policy.futurePlanningHorizon().toString(),
                sections,
                Map.copyOf(limits),
                policy.measurementGranularity().name());
    }

    private record PolicyMaterial(
            java.time.Instant historyStart,
            String futurePlanningHorizon,
            List<String> includedSections,
            Map<String, Integer> maxRowsBySection,
            String measurementGranularity
    ) {}

    private record FingerprintMaterial(
            String schemaVersion,
            java.time.Instant asOf,
            PolicyMaterial policy,
            EquipmentLifecycleContextV1.ConsistencyMetadata consistency,
            EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.EquipmentCore> equipment,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.HierarchyNode> hierarchy,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.TechnicalAttribute> technicalAttributes,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.LifecycleEvent> lifecycleHistory,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.MeterReadingItem> meterHistory,
            EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.MaintenanceSummary> maintenanceSummary,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.MaintenanceEvent> maintenanceHistory,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.PlannedMaintenanceItem> plannedMaintenance,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.WorkOrderItem> workOrders,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.DefectItem> defects,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.RepairRequestItem> repairRequests,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.InspectionItem> inspections,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.ConditionMeasurementItem> conditionMeasurements,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.InstalledComponentItem> installedComponents,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.ComponentReplacementItem> componentReplacementHistory,
            EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.WarrantyInfo> warranty,
            EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.CostSummary> costSummary,
            EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.EnvironmentContext> environmentContext,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.DocumentMetadata> documentMetadata,
            com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality dataQuality,
            java.util.List<EquipmentLifecycleContextV1.SourceWatermark> sourceWatermarks
    ) {}
}
