package com.toir.service.maintanance;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceTriggerPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MaintenanceScheduleSourceItemKeyGeneratorTest {

    private final MaintenanceScheduleSourceItemKeyGenerator generator =
            new MaintenanceScheduleSourceItemKeyGenerator();

    @Test
    void sameCanonicalCoordinatesProduceSameVersionOneLowercaseSha256Key() {
        MaintenanceScheduleSourceItemCoordinates first = coordinates(
                uuid("11111111-1111-1111-1111-111111111111"),
                uuid("33333333-3333-3333-3333-333333333333"),
                LocalDate.of(2026, 3, 15),
                7L
        );
        MaintenanceScheduleSourceItemCoordinates independentlyBuilt = coordinates(
                uuid("11111111-1111-1111-1111-111111111111"),
                uuid("33333333-3333-3333-3333-333333333333"),
                LocalDate.of(2026, 3, 15),
                7L
        );

        assertThat(generator.version()).isEqualTo(1);
        assertThat(generator.generate(first))
                .isEqualTo(generator.generate(independentlyBuilt))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void sourceKeysDoNotDependOnInputCollectionOrder() {
        MaintenanceScheduleSourceItemCoordinates first = coordinates(
                uuid("11111111-1111-1111-1111-111111111111"),
                uuid("33333333-3333-3333-3333-333333333333"),
                LocalDate.of(2026, 3, 15),
                7L
        );
        MaintenanceScheduleSourceItemCoordinates second = coordinates(
                uuid("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                uuid("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                LocalDate.of(2026, 4, 15),
                8L
        );

        Map<UUID, String> forward = keysByEquipment(List.of(first, second));
        Map<UUID, String> reversed = keysByEquipment(List.of(second, first));

        assertThat(reversed).isEqualTo(forward);
    }

    @Test
    void changingEquipmentRuleDateOrCycleChangesTheKey() {
        MaintenanceScheduleSourceItemCoordinates baseline = coordinates(
                uuid("11111111-1111-1111-1111-111111111111"),
                uuid("33333333-3333-3333-3333-333333333333"),
                LocalDate.of(2026, 3, 15),
                7L
        );
        String baselineKey = generator.generate(baseline);

        assertThat(generator.generate(coordinates(
                uuid("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                baseline.maintenanceRuleId(),
                baseline.plannedDate(),
                baseline.cycleOrdinal()
        ))).isNotEqualTo(baselineKey);
        assertThat(generator.generate(coordinates(
                baseline.equipmentId(),
                uuid("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                baseline.plannedDate(),
                baseline.cycleOrdinal()
        ))).isNotEqualTo(baselineKey);
        assertThat(generator.generate(coordinates(
                baseline.equipmentId(),
                baseline.maintenanceRuleId(),
                baseline.plannedDate().plusDays(1),
                baseline.cycleOrdinal()
        ))).isNotEqualTo(baselineKey);
        assertThat(generator.generate(coordinates(
                baseline.equipmentId(),
                baseline.maintenanceRuleId(),
                baseline.plannedDate(),
                baseline.cycleOrdinal() + 1
        ))).isNotEqualTo(baselineKey);
    }

    @Test
    void localizedDisplayNamesAreOutsideCanonicalIdentityInput() {
        assertThat(MaintenanceScheduleSourceItemCoordinates.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain(
                        "equipmentNameSnapshot",
                        "regulationNameSnapshot",
                        "maintenanceRuleNameSnapshot",
                        "templateNameSnapshot",
                        "taskTitleSnapshot"
                );
    }

    @Test
    void nullAndLiteralNullMarkerRemainDifferentCanonicalValues() {
        MaintenanceScheduleSourceItemCoordinates baseline = coordinates(
                uuid("11111111-1111-1111-1111-111111111111"),
                uuid("33333333-3333-3333-3333-333333333333"),
                LocalDate.of(2026, 3, 15),
                7L
        );
        MaintenanceScheduleSourceItemCoordinates nullDiscriminator =
                withDiscriminator(baseline, null);
        MaintenanceScheduleSourceItemCoordinates literalDiscriminator =
                withDiscriminator(baseline, "<null>");

        assertThat(generator.generate(nullDiscriminator))
                .isNotEqualTo(generator.generate(literalDiscriminator));
    }

    private static MaintenanceScheduleSourceItemCoordinates withDiscriminator(
            MaintenanceScheduleSourceItemCoordinates source,
            String triggerDiscriminator) {
        return new MaintenanceScheduleSourceItemCoordinates(
                source.equipmentId(),
                source.regulationId(),
                source.maintenanceRuleId(),
                source.templateId(),
                source.triggerType(),
                triggerDiscriminator,
                source.maintenanceType(),
                source.plannedDate(),
                source.scheduledStart(),
                source.cycleOrdinal()
        );
    }

    private Map<UUID, String> keysByEquipment(
            List<MaintenanceScheduleSourceItemCoordinates> coordinates) {
        return coordinates.stream().collect(Collectors.toMap(
                MaintenanceScheduleSourceItemCoordinates::equipmentId,
                generator::generate
        ));
    }

    private static MaintenanceScheduleSourceItemCoordinates coordinates(
            UUID equipmentId,
            UUID maintenanceRuleId,
            LocalDate plannedDate,
            long cycleOrdinal) {
        return new MaintenanceScheduleSourceItemCoordinates(
                equipmentId,
                uuid("22222222-2222-2222-2222-222222222222"),
                maintenanceRuleId,
                null,
                MaintenanceTriggerPolicy.ANY,
                "calendar",
                MaintenanceKind.PREVENTIVE,
                plannedDate,
                LocalDateTime.of(plannedDate, java.time.LocalTime.of(8, 30)),
                cycleOrdinal
        );
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
