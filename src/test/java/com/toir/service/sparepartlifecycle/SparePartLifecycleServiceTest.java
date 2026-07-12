package com.toir.service.sparepartlifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.sparepartlifecycle.InstallSparePartCommand;
import com.toir.dto.sparepartlifecycle.RemoveSparePartCommand;
import com.toir.dto.sparepartlifecycle.ReplaceSparePartCommand;
import com.toir.dto.sparepartlifecycle.ReplacementPartCommand;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluation;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.equipment.EquipmentSparePart;
import com.toir.entity.equipment.MeterReading;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.entity.sparepartlifecycle.SparePartInstallationMaterialAllocation;
import com.toir.entity.sparepartlifecycle.SparePartInstallationMeterBaseline;
import com.toir.entity.sparepartlifecycle.SparePartLifeLimit;
import com.toir.entity.sparepartlifecycle.SparePartLifeRule;
import com.toir.entity.sparepartlifecycle.SparePartLifecycleCommand;
import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandType;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.enums.sparepartlifecycle.SparePartRemovalDisposition;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentSparePartRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationMaterialAllocationRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationMeterBaselineRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import com.toir.repository.sparepartlifecycle.SparePartLifeLimitRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparePartLifecycleServiceTest {

    @Mock SparePartLifecycleCommandCoordinator commandCoordinator;
    @Mock EquipmentRepository equipmentRepository;
    @Mock EquipmentNodeRepository equipmentNodeRepository;
    @Mock SparePartRepository sparePartRepository;
    @Mock EquipmentSparePartRepository equipmentSparePartRepository;
    @Mock SparePartInstallationRepository installationRepository;
    @Mock SparePartLifeRuleResolver ruleResolver;
    @Mock SparePartLifeLimitRepository limitRepository;
    @Mock CanonicalEquipmentMeterService canonicalMeterService;
    @Mock MeterReadingRepository meterReadingRepository;
    @Mock SparePartInstallationMeterBaselineRepository baselineRepository;
    @Mock RepairMaterialUsageRepository materialUsageRepository;
    @Mock SparePartInstallationMaterialAllocationRepository allocationRepository;
    @Mock SparePartLifecycleEvaluator evaluator;
    @Mock SparePartDueEventService dueEventService;
    @Mock ScopeAccessService scopeAccessService;
    @Mock SparePartSlotNormalizer slotNormalizer;
    @Mock AuditBuilderService auditBuilderService;
    @Spy ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks SparePartLifecycleService service;

    @Test
    void installCapturesImmutableRuleAndMeterBaselineWithoutChangingParentLifetime() {
        UUID actorId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        Instant installedAt = Instant.parse("2026-07-10T09:00:00Z");
        InstallSparePartCommand request = new InstallSparePartCommand(
                nodeId,
                "front left",
                "Front left brake",
                partId,
                BigDecimal.ONE,
                "SER-100",
                "LOT-2026-07",
                installedAt,
                workOrderId,
                usageId,
                null,
                "Installed after inspection"
        );
        SparePartLifecycleCommand lifecycleCommand = new SparePartLifecycleCommand();
        lifecycleCommand.setId(UUID.randomUUID());
        lifecycleCommand.setCommandType(SparePartLifecycleCommandType.INSTALL);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setLifetimeLimitValue(50_000.0);
        equipment.setLifetimeBaselineValue(5_000.0);
        EquipmentNode node = new EquipmentNode();
        node.setId(nodeId);
        node.setEquipmentId(equipmentId);
        SparePart part = new SparePart();
        part.setId(partId);
        EquipmentSparePart bom = new EquipmentSparePart();
        bom.setEquipmentId(equipmentId);
        bom.setSparePartId(partId);
        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(usageId);
        usage.setWorkOrderId(workOrderId);
        usage.setSparePartId(partId);
        usage.setQuantity(java.math.BigDecimal.ONE);
        usage.setSerialNumber("SER-100");
        usage.setLotNumber("LOT-2026-07");
        SparePartLifeRule rule = new SparePartLifeRule();
        rule.setId(UUID.randomUUID());
        rule.setRevision(4);
        rule.setCombinationMode(SparePartLifeCombinationMode.ANY);
        rule.setDueAction(SparePartDueAction.MAINTENANCE_REQUIRED);
        SparePartLifeLimit limit = new SparePartLifeLimit();
        limit.setId(UUID.randomUUID());
        limit.setRuleId(rule.getId());
        limit.setLimitKind(SparePartLifeLimitKind.METER);
        limit.setMeterType(MeterType.MILEAGE_KM);
        limit.setLimitValue(new BigDecimal("10000"));
        limit.setWarningBeforeValue(new BigDecimal("1000"));
        limit.setSequence(0);
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(meterId);
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(MeterType.MILEAGE_KM);
        meter.setCurrentValue(12_345.5);
        meter.setActive(true);
        MeterReading currentReading = new MeterReading();
        currentReading.setId(UUID.randomUUID());
        currentReading.setReadAt(installedAt.minusSeconds(60));

        when(commandCoordinator.acquire("install-1", SparePartLifecycleCommandType.INSTALL, request, actorId, workOrderId))
                .thenReturn(new SparePartLifecycleCommandCoordinator.LifecycleCommandHandle(lifecycleCommand, false));
        when(equipmentRepository.findByIdAndIsDeletedFalseForUpdate(equipmentId)).thenReturn(Optional.of(equipment));
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.of(node));
        when(slotNormalizer.normalizeRequired("front left")).thenReturn("FRONT_LEFT");
        when(slotNormalizer.positionKey(nodeId, "FRONT_LEFT")).thenReturn("N:" + nodeId + ":S:FRONT_LEFT");
        when(installationRepository.findActiveByEquipmentIdAndPositionKeyForUpdate(equipmentId, "N:" + nodeId + ":S:FRONT_LEFT"))
                .thenReturn(Optional.empty());
        when(sparePartRepository.findByIdAndIsDeletedFalse(partId)).thenReturn(Optional.of(part));
        when(equipmentSparePartRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(bom));
        when(materialUsageRepository.findByIdAndIsDeletedFalseForUpdate(usageId)).thenReturn(Optional.of(usage));
        when(allocationRepository.existsByRepairMaterialUsageIdAndIsDeletedFalse(usageId)).thenReturn(false);
        when(ruleResolver.resolve(partId, equipmentId, nodeId, "FRONT_LEFT", installedAt)).thenReturn(Optional.of(rule));
        when(limitRepository.findAllByRuleIdAndIsDeletedFalseOrderBySequenceAsc(rule.getId())).thenReturn(List.of(limit));
        when(canonicalMeterService.resolve(equipmentId, MeterType.MILEAGE_KM, null)).thenReturn(meter);
        when(meterReadingRepository.findTopByMeterIdAndIsDeletedFalseOrderByReadAtDesc(meterId))
                .thenReturn(Optional.of(currentReading));
        when(installationRepository.existsBySparePartIdAndSerialNumberSnapshotIgnoreCaseAndIsDeletedFalse(partId, "SER-100"))
                .thenReturn(false);
        when(installationRepository.save(any(SparePartInstallation.class))).thenAnswer(invocation -> {
            SparePartInstallation installation = invocation.getArgument(0);
            installation.setId(UUID.randomUUID());
            return installation;
        });
        when(baselineRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(allocationRepository.save(any(SparePartInstallationMaterialAllocation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(evaluator.evaluate(any())).thenAnswer(invocation -> {
            var input = (com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluationInput) invocation.getArgument(0);
            return new SparePartLifecycleEvaluation(
                    input.installationId(),
                    SparePartLifecycleEvaluationState.OK,
                    SparePartDueAction.MAINTENANCE_REQUIRED,
                    List.of(),
                    null,
                    List.of(),
                    installedAt
            );
        });

        var result = service.install(equipmentId, "install-1", actorId, request);

        assertThat(result.replay()).isFalse();
        assertThat(result.installation().getStatus()).isEqualTo(SparePartInstallationStatus.ACTIVE);
        assertThat(result.installation().getPositionKey()).isEqualTo("N:" + nodeId + ":S:FRONT_LEFT");
        assertThat(result.installation().getAppliedRuleRevision()).isEqualTo(4);
        assertThat(result.installation().getAppliedRuleSnapshot()).contains("10000");
        assertThat(equipment.getLifetimeLimitValue()).isEqualTo(50_000.0);
        assertThat(equipment.getLifetimeBaselineValue()).isEqualTo(5_000.0);

        ArgumentCaptor<Iterable<SparePartInstallationMeterBaseline>> baselines = ArgumentCaptor.forClass(Iterable.class);
        verify(baselineRepository).saveAll(baselines.capture());
        SparePartInstallationMeterBaseline baseline = baselines.getValue().iterator().next();
        assertThat(baseline.getEquipmentMeterId()).isEqualTo(meterId);
        assertThat(baseline.getBaselineValue()).isEqualByComparingTo("12345.5");
        assertThat(baseline.getBaselineReadingId()).isEqualTo(currentReading.getId());
        verify(commandCoordinator).complete(lifecycleCommand, result.installation().getId(), null);
    }

    @Test
    void removeStoresExplicitDispositionAndResolvesDueEvents() {
        UUID actorId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        Instant removedAt = Instant.parse("2026-07-10T10:00:00Z");
        RemoveSparePartCommand request = new RemoveSparePartCommand(
                installationId,
                removedAt,
                workOrderId,
                SparePartRemovalDisposition.SCRAP,
                "Service life exhausted",
                null
        );
        SparePartLifecycleCommand lifecycleCommand = new SparePartLifecycleCommand();
        lifecycleCommand.setId(UUID.randomUUID());
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(installationId);
        installation.setEquipmentId(equipmentId);
        installation.setStatus(SparePartInstallationStatus.ACTIVE);
        installation.setInstalledAt(removedAt.minusSeconds(3600));
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setDepartmentId(UUID.randomUUID());
        when(commandCoordinator.acquire("remove-1", SparePartLifecycleCommandType.REMOVE, request, actorId, workOrderId))
                .thenReturn(new SparePartLifecycleCommandCoordinator.LifecycleCommandHandle(lifecycleCommand, false));
        when(installationRepository.findByIdAndIsDeletedFalseForUpdate(installationId))
                .thenReturn(Optional.of(installation));
        when(equipmentRepository.findByIdAndIsDeletedFalseForUpdate(equipmentId)).thenReturn(Optional.of(equipment));
        when(installationRepository.save(installation)).thenReturn(installation);

        var result = service.remove("remove-1", actorId, request);

        assertThat(result.installation()).isNull();
        assertThat(result.removedInstallation()).isSameAs(installation);
        assertThat(installation.getStatus()).isEqualTo(SparePartInstallationStatus.REMOVED);
        assertThat(installation.getRemovalDisposition()).isEqualTo(SparePartRemovalDisposition.SCRAP);
        assertThat(installation.getRemovalReason()).isEqualTo("Service life exhausted");
        verify(dueEventService).resolveForRemoval(installationId, removedAt, actorId, null);
        verify(commandCoordinator).complete(lifecycleCommand, null, installationId);
    }

    @Test
    void removeRejectsReturnToStockWhenNoTransactionalWmsPathIsConfigured() {
        UUID actorId = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        RemoveSparePartCommand request = new RemoveSparePartCommand(
                installationId,
                Instant.parse("2026-07-10T10:00:00Z"),
                null,
                SparePartRemovalDisposition.RETURN_TO_STOCK,
                "Reusable",
                null
        );

        assertThatThrownBy(() -> service.remove("remove-stock", actorId, request))
                .hasMessageStartingWith("RETURN_TO_STOCK_UNSUPPORTED:");
    }

    @Test
    void replaceAtomicallyClosesOldInstallationAndLinksNewInstallationAtSamePosition() {
        UUID actorId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID oldId = UUID.randomUUID();
        UUID newPartId = UUID.randomUUID();
        Instant replacedAt = Instant.parse("2026-07-10T10:00:00Z");
        ReplaceSparePartCommand request = new ReplaceSparePartCommand(
                oldId,
                new ReplacementPartCommand(
                        newPartId,
                        BigDecimal.ONE,
                        null,
                        "LOT-NEW",
                        null,
                        "External certified stock"
                ),
                replacedAt,
                null,
                SparePartRemovalDisposition.SCRAP,
                "Expired",
                null
        );
        SparePartLifecycleCommand lifecycleCommand = new SparePartLifecycleCommand();
        lifecycleCommand.setId(UUID.randomUUID());
        SparePartInstallation old = new SparePartInstallation();
        old.setId(oldId);
        old.setEquipmentId(equipmentId);
        old.setEquipmentNodeId(UUID.randomUUID());
        old.setNormalizedSlotCode("FRONT_LEFT");
        old.setPositionKey("N:" + old.getEquipmentNodeId() + ":S:FRONT_LEFT");
        old.setPositionLabelSnapshot("Front left brake");
        old.setStatus(SparePartInstallationStatus.ACTIVE);
        old.setInstalledAt(replacedAt.minusSeconds(86_400));
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setDepartmentId(UUID.randomUUID());
        SparePart newPart = new SparePart();
        newPart.setId(newPartId);
        when(commandCoordinator.acquire("replace-1", SparePartLifecycleCommandType.REPLACE, request, actorId, null))
                .thenReturn(new SparePartLifecycleCommandCoordinator.LifecycleCommandHandle(lifecycleCommand, false));
        when(installationRepository.findByIdAndIsDeletedFalseForUpdate(oldId)).thenReturn(Optional.of(old));
        when(equipmentRepository.findByIdAndIsDeletedFalseForUpdate(equipmentId)).thenReturn(Optional.of(equipment));
        when(sparePartRepository.findByIdAndIsDeletedFalse(newPartId)).thenReturn(Optional.of(newPart));
        when(equipmentSparePartRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());
        when(ruleResolver.resolve(newPartId, equipmentId, old.getEquipmentNodeId(), "FRONT_LEFT", replacedAt))
                .thenReturn(Optional.empty());
        when(installationRepository.save(any(SparePartInstallation.class))).thenAnswer(invocation -> {
            SparePartInstallation saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            return saved;
        });
        when(evaluator.evaluate(any())).thenAnswer(invocation -> {
            var input = (com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluationInput) invocation.getArgument(0);
            return new SparePartLifecycleEvaluation(
                    input.installationId(),
                    SparePartLifecycleEvaluationState.OK,
                    SparePartDueAction.WARNING_ONLY,
                    List.of(),
                    null,
                    List.of(),
                    replacedAt
            );
        });

        var result = service.replace("replace-1", actorId, request);

        SparePartInstallation replacement = result.installation();
        assertThat(old.getStatus()).isEqualTo(SparePartInstallationStatus.REPLACED);
        assertThat(old.getReplacedByInstallationId()).isEqualTo(replacement.getId());
        assertThat(replacement.getReplacesInstallationId()).isEqualTo(oldId);
        assertThat(replacement.getPositionKey()).isEqualTo(old.getPositionKey());
        assertThat(replacement.getReplacementCorrelationId()).isEqualTo(old.getReplacementCorrelationId());
        verify(dueEventService).resolveForRemoval(oldId, replacedAt, actorId, replacement.getId());
        verify(commandCoordinator).complete(lifecycleCommand, replacement.getId(), oldId);
    }
}
