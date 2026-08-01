package com.toir.service.equipmentlifecycle;

import static com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.HIERARCHY;
import static com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection.INSTALLED_COMPONENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextPolicy;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentNodeType;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.TechnicalDocumentRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentLocationHistoryRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentStatusHistoryRepository;
import com.toir.repository.inspection.EquipmentInspectionLifecycleRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentLifecycleContextAssemblerTest {

    @Mock EquipmentRepository equipmentRepository;
    @Mock EquipmentPassportRepository passportRepository;
    @Mock EquipmentNodeRepository nodeRepository;
    @Mock EquipmentAttributeValueRepository attributeValueRepository;
    @Mock EquipmentAttributeDefinitionRepository attributeDefinitionRepository;
    @Mock EquipmentStatusHistoryRepository statusHistoryRepository;
    @Mock EquipmentLocationHistoryRepository locationHistoryRepository;
    @Mock MeterReadingRepository meterReadingRepository;
    @Mock EquipmentMeterRepository meterRepository;
    @Mock MaintenanceCompletionAnchorRepository completionAnchorRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock PprTaskRepository pprTaskRepository;
    @Mock MaintenanceDueEventRepository dueEventRepository;
    @Mock DefectRepository defectRepository;
    @Mock RepairRequestRepository repairRequestRepository;
    @Mock EquipmentInspectionLifecycleRepository inspectionRepository;
    @Mock ConditionReadingRepository conditionReadingRepository;
    @Mock SparePartInstallationRepository installationRepository;
    @Mock TechnicalDocumentRepository documentRepository;
    @Mock ActualCostRepository actualCostRepository;
    @Mock EquipmentLifecycleCanonicalMapper canonicalMapper;
    @Mock EquipmentLifecycleFingerprintService fingerprintService;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-31T12:00:00Z"), ZoneOffset.UTC);
    private EquipmentLifecycleContextAssembler assembler;
    private UUID equipmentId;
    private Instant asOf;

    @BeforeEach
    void setUp() {
        assembler = new EquipmentLifecycleContextAssembler(
                equipmentRepository,
                passportRepository,
                nodeRepository,
                attributeValueRepository,
                attributeDefinitionRepository,
                statusHistoryRepository,
                locationHistoryRepository,
                meterReadingRepository,
                meterRepository,
                completionAnchorRepository,
                workOrderRepository,
                pprTaskRepository,
                dueEventRepository,
                defectRepository,
                repairRequestRepository,
                inspectionRepository,
                conditionReadingRepository,
                installationRepository,
                documentRepository,
                actualCostRepository,
                canonicalMapper,
                fingerprintService,
                clock);
        equipmentId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        asOf = Instant.parse("2026-07-31T11:00:00Z");
    }

    @Test
    void appliesLimitPlusOneAndReportsTruncationWithoutLoadingOtherSections() {
        Equipment equipment = equipment();
        EquipmentNode first = node(
                "00000000-0000-0000-0000-000000000201", "A");
        EquipmentNode sentinel = node(
                "00000000-0000-0000-0000-000000000202", "B");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment));
        when(passportRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.empty());
        when(nodeRepository.findLifecycleNodes(equipmentId, 2))
                .thenReturn(List.of(first, sentinel));
        when(fingerprintService.fingerprint(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn("f".repeat(64));

        var context = assembler.assemble(equipmentId, asOf, policy());

        assertThat(context.hierarchy().items()).singleElement()
                .extracting(item -> item.sourceId())
                .isEqualTo(first.getId());
        assertThat(context.hierarchy().metadata().truncated()).isTrue();
        assertThat(context.hierarchy().metadata().returnedCount()).isEqualTo(1);
        assertThat(context.meterHistory().metadata().availability())
                .isEqualTo(EquipmentLifecycleDataQuality.Availability.OUT_OF_SCOPE_FOR_V1);
        assertThat(context.meterHistory().items()).isEmpty();
        assertThat(context.generatedAt()).isEqualTo(clock.instant());
        verify(nodeRepository).findLifecycleNodes(equipmentId, 2);
    }

    @Test
    void softDeletedOrMissingEquipmentUsesCanonicalNotFoundLookup() {
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> assembler.assemble(equipmentId, asOf, policy()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining(equipmentId.toString());
    }

    @Test
    void issuedMaterialIsNotUsedAsInstallationEvidence() {
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment()));
        when(passportRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.empty());
        when(installationRepository.findLifecycleInstallations(
                equipmentId,
                Instant.parse("2026-01-01T00:00:00Z"),
                asOf,
                2))
                .thenReturn(List.of());
        when(fingerprintService.fingerprint(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn("f".repeat(64));
        EquipmentLifecycleContextPolicy policy = new EquipmentLifecycleContextPolicy(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ZERO,
                Set.of(INSTALLED_COMPONENTS),
                Map.of(INSTALLED_COMPONENTS, 1),
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW);

        var context = assembler.assemble(equipmentId, asOf, policy);

        assertThat(context.installedComponents().items()).isEmpty();
        assertThat(context.installedComponents().metadata().availability())
                .isEqualTo(
                        EquipmentLifecycleDataQuality.Availability.AVAILABLE_BUT_OPTIONAL);
        verify(installationRepository).findLifecycleInstallations(
                equipmentId,
                Instant.parse("2026-01-01T00:00:00Z"),
                asOf,
                2);
    }

    private EquipmentLifecycleContextPolicy policy() {
        return new EquipmentLifecycleContextPolicy(
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ofDays(90),
                Set.of(HIERARCHY),
                Map.of(HIERARCHY, 1),
                EquipmentLifecycleContextPolicy.MeasurementGranularity.RAW);
    }

    private Equipment equipment() {
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setUpdatedAt(Instant.parse("2026-07-30T00:00:00Z"));
        equipment.setCode("EQ-SYNTHETIC");
        equipment.setName("Synthetic pump");
        equipment.setInventoryNumber("INV-SYNTHETIC");
        equipment.setEquipmentTypeId(
                UUID.fromString("00000000-0000-0000-0000-000000000102"));
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        equipment.setHasWarranty(false);
        return equipment;
    }

    private EquipmentNode node(String id, String code) {
        EquipmentNode node = new EquipmentNode();
        node.setId(UUID.fromString(id));
        node.setEquipmentId(equipmentId);
        node.setCode(code);
        node.setName("Synthetic node " + code);
        node.setNodeType(EquipmentNodeType.ASSEMBLY);
        node.setUpdatedAt(Instant.parse("2026-07-30T00:00:00Z"));
        return node;
    }
}
