package com.toir.service.maintanance;

import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewItem;
import com.toir.entity.PprPlan;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceScheduleAnchorSource;
import com.toir.enums.PeriodicityUnit;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaintenanceScheduleSnapshotDraftFactoryTest {

    @Test
    void createsImmutableDraftRowsWithCanonicalWindowAndHistoricalMetadata() {
        EquipmentMaintenanceRuleRepository ruleRepository =
                mock(EquipmentMaintenanceRuleRepository.class);
        MaintenanceRegulationRepository regulationRepository =
                mock(MaintenanceRegulationRepository.class);
        PprTaskScheduleWindowCalculator calculator =
                new PprTaskScheduleWindowCalculator();
        MaintenanceScheduleSnapshotDraftFactory factory =
                new MaintenanceScheduleSnapshotDraftFactory(
                        ruleRepository,
                        regulationRepository,
                        calculator,
                        new MaintenanceScheduleSourceItemKeyGenerator());
        UUID ruleId = UUID.randomUUID();
        EquipmentMaintenanceRule rule = new EquipmentMaintenanceRule();
        rule.setId(ruleId);
        rule.setCode("MR-001");
        rule.setName("Bearing service");
        rule.setLeadTimeDays(3);
        when(ruleRepository.findAllById(List.of(ruleId))).thenReturn(List.of(rule));

        PprPlan plan = new PprPlan();
        plan.setId(UUID.randomUUID());
        plan.setStartDate(LocalDate.of(2026, 1, 1));
        plan.setEndDate(LocalDate.of(2026, 12, 31));
        plan.setDepartmentId(UUID.randomUUID());

        var rows = factory.create(
                plan,
                1L,
                List.of(new MaintenanceSchedulePreviewItem(
                        UUID.randomUUID(),
                        "EQ-001",
                        "Pump 1",
                        UUID.randomUUID(),
                        ruleId,
                        "Bearing service",
                        MaintenanceKind.PREVENTIVE,
                        PeriodicityUnit.MONTH,
                        1,
                        LocalDate.of(2026, 7, 27),
                        MaintenanceScheduleAnchorSource.PLAN_START,
                        16.0d,
                        false)));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getCalculationRevision()).isEqualTo(1L);
        assertThat(rows.getFirst().getSourceCodeSnapshot()).isEqualTo("MR-001");
        assertThat(rows.getFirst().getSourceNameSnapshot()).isEqualTo("Bearing service");
        assertThat(rows.getFirst().getSourceItemKey()).matches("^[0-9a-f]{64}$");
        assertThat(rows.getFirst().getSourceItemKeyVersion()).isEqualTo(1);
        assertThat(rows.getFirst().getWorkOrderLeadDays()).isEqualTo(3);
        assertThat(rows.getFirst().getEquipmentCodeSnapshot()).isEqualTo("EQ-001");
        assertThat(rows.getFirst().getScheduledStart())
                .isBefore(rows.getFirst().getScheduledEnd());
        assertThat(rows.getFirst().getDueDate())
                .isAfterOrEqualTo(rows.getFirst().getScheduledEnd());
    }

    @Test
    void rejectsSnapshotWhenReferencedDisplayMetadataCannotBeFrozen() {
        MaintenanceScheduleSnapshotDraftFactory factory =
                new MaintenanceScheduleSnapshotDraftFactory(
                        mock(EquipmentMaintenanceRuleRepository.class),
                        mock(MaintenanceRegulationRepository.class),
                        new PprTaskScheduleWindowCalculator(),
                        new MaintenanceScheduleSourceItemKeyGenerator());
        PprPlan plan = new PprPlan();
        plan.setStartDate(LocalDate.of(2026, 1, 1));
        plan.setEndDate(LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> factory.create(
                plan,
                1L,
                List.of(new MaintenanceSchedulePreviewItem(
                        UUID.randomUUID(),
                        "EQ-001",
                        "Pump 1",
                        null,
                        UUID.randomUUID(),
                        "Bearing service",
                        MaintenanceKind.PREVENTIVE,
                        PeriodicityUnit.MONTH,
                        1,
                        LocalDate.of(2026, 7, 27),
                        MaintenanceScheduleAnchorSource.PLAN_START,
                        8.0d,
                        false))))
                .isInstanceOfSatisfying(RestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        "PPR_CALCULATION_SOURCE_METADATA_MISSING"));
    }
}
