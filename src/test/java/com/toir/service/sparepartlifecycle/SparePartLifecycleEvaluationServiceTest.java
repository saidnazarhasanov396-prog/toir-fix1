package com.toir.service.sparepartlifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.sparepartlifecycle.AppliedLifeLimitSnapshot;
import com.toir.dto.sparepartlifecycle.AppliedLifeRuleSnapshot;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.entity.sparepartlifecycle.SparePartInstallationMeterBaseline;
import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationMeterBaselineRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparePartLifecycleEvaluationServiceTest {

    @Mock SparePartInstallationRepository installationRepository;
    @Mock SparePartInstallationMeterBaselineRepository baselineRepository;
    @Mock EquipmentMeterRepository meterRepository;
    @Mock SparePartDueEventService dueEventService;
    @Mock com.toir.repository.equipment.EquipmentRepository equipmentRepository;
    @Mock com.toir.security.ScopeAccessService scopeAccessService;
    @Mock com.toir.util.AuditBuilderService auditBuilderService;

    private SparePartLifecycleEvaluationService service;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        service = new SparePartLifecycleEvaluationService(
                installationRepository,
                baselineRepository,
                meterRepository,
                new SparePartLifecycleEvaluator(),
                dueEventService,
                objectMapper,
                equipmentRepository,
                scopeAccessService,
                auditBuilderService
        );
        when(installationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reevaluateUsesImmutableSnapshotAndCapturedBaselineThenPersistsDueState() throws Exception {
        UUID meterId = UUID.randomUUID();
        SparePartInstallation installation = installation(meterId);
        SparePartInstallationMeterBaseline baseline = new SparePartInstallationMeterBaseline();
        baseline.setEquipmentMeterId(meterId);
        baseline.setBaselineValue(new BigDecimal("100.000000"));
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(meterId);
        meter.setActive(true);
        meter.setCurrentValue(150);

        when(installationRepository.findByIdAndIsDeletedFalseForUpdate(installation.getId()))
                .thenReturn(Optional.of(installation));
        when(baselineRepository.findAllByInstallationIdAndIsDeletedFalse(installation.getId()))
                .thenReturn(List.of(baseline));
        when(meterRepository.findAllByIdInAndIsDeletedFalse(List.of(meterId))).thenReturn(List.of(meter));

        var result = service.reevaluate(installation.getId(), Instant.parse("2026-07-10T12:00:00Z"));

        assertThat(result.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.DUE);
        assertThat(installation.getLifecycleEvaluationState()).isEqualTo(SparePartLifecycleEvaluationState.DUE);
        assertThat(installation.getLastEvaluatedAt()).isEqualTo(Instant.parse("2026-07-10T12:00:00Z"));
        assertThat(installation.getEvaluationDetails()).contains("\"aggregateState\":\"DUE\"");
        verify(dueEventService).applyEvaluation(installation, result);
    }

    @Test
    void manualDueRequiresReasonAndDedicatedPermission() {
        UUID installationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        assertThatThrownBy(() -> service.markManualDue(installationId, actorId, "reason"))
                .isInstanceOf(com.toir.exception.RestException.class);

        when(scopeAccessService.hasAuthority(com.toir.security.PermissionConstants.WILDCARD)).thenReturn(true);
        assertThatThrownBy(() -> service.markManualDue(installationId, actorId, " "))
                .isInstanceOfSatisfying(com.toir.exception.RestException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                com.toir.exception.SparePartLifecycleErrorCodes.MANUAL_DUE_REASON_MISSING));
    }

    @Test
    void manualDueRejectsNonManualRule() throws Exception {
        UUID installationId = UUID.randomUUID();
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(installationId);
        installation.setStatus(com.toir.enums.sparepartlifecycle.SparePartInstallationStatus.ACTIVE);
        installation.setAppliedRuleSnapshot(objectMapper.writeValueAsString(
                new AppliedLifeRuleSnapshot(UUID.randomUUID(), 1,
                        com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode.ANY,
                        com.toir.enums.sparepartlifecycle.SparePartDueAction.WARNING_ONLY,
                        List.of())));
        when(scopeAccessService.hasAuthority(com.toir.security.PermissionConstants.WILDCARD)).thenReturn(true);
        when(installationRepository.findByIdAndIsDeletedFalseForUpdate(installationId))
                .thenReturn(Optional.of(installation));

        assertThatThrownBy(() -> service.markManualDue(installationId, UUID.randomUUID(), "inspection"))
                .isInstanceOfSatisfying(com.toir.exception.RestException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                com.toir.exception.SparePartLifecycleErrorCodes.MANUAL_DUE_NOT_ALLOWED));
    }

    @Test
    void meterTriggerReevaluatesOnlyActiveInstallationsUsingThatCapturedMeter() throws Exception {
        UUID meterId = UUID.randomUUID();
        SparePartInstallation installation = installation(meterId);
        SparePartInstallationMeterBaseline baseline = new SparePartInstallationMeterBaseline();
        baseline.setInstallationId(installation.getId());
        baseline.setEquipmentMeterId(meterId);
        baseline.setBaselineValue(BigDecimal.ZERO);
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(meterId);
        meter.setActive(true);
        meter.setCurrentValue(10);

        when(baselineRepository.findAllByEquipmentMeterIdAndIsDeletedFalse(meterId)).thenReturn(List.of(baseline));
        when(installationRepository.findAllByIdInAndStatusAndIsDeletedFalse(
                List.of(installation.getId()), SparePartInstallationStatus.ACTIVE)).thenReturn(List.of(installation));
        when(installationRepository.findByIdAndIsDeletedFalseForUpdate(installation.getId()))
                .thenReturn(Optional.of(installation));
        when(baselineRepository.findAllByInstallationIdAndIsDeletedFalse(installation.getId()))
                .thenReturn(List.of(baseline));
        when(meterRepository.findAllByIdInAndIsDeletedFalse(List.of(meterId))).thenReturn(List.of(meter));

        assertThat(service.reevaluateForMeter(meterId, Instant.parse("2026-07-10T12:00:00Z")))
                .hasSize(1);
        verify(installationRepository).save(installation);
    }

    private SparePartInstallation installation(UUID meterId) throws Exception {
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(UUID.randomUUID());
        installation.setEquipmentId(UUID.randomUUID());
        installation.setStatus(SparePartInstallationStatus.ACTIVE);
        installation.setInstalledAt(Instant.parse("2026-07-10T10:00:00Z"));
        AppliedLifeRuleSnapshot snapshot = new AppliedLifeRuleSnapshot(
                UUID.randomUUID(),
                1,
                SparePartLifeCombinationMode.ANY,
                SparePartDueAction.MAINTENANCE_REQUIRED,
                List.of(new AppliedLifeLimitSnapshot(
                        UUID.randomUUID(),
                        SparePartLifeLimitKind.METER,
                        null,
                        MeterType.ENGINE_HOURS,
                        meterId,
                        new BigDecimal("50"),
                        new BigDecimal("5"),
                        1
                ))
        );
        installation.setAppliedRuleSnapshot(objectMapper.writeValueAsString(snapshot));
        return installation;
    }
}
