package com.toir.service.maintanance;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleRecurrenceAnchor;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.PprScopeType;
import com.toir.enums.PriorityLevel;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class MaintenanceScheduleContentTestFixtures {

    static final UUID EQUIPMENT_A =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID EQUIPMENT_B =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final UUID REGULATION =
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    static final UUID DEPARTMENT =
            UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    static final UUID EQUIPMENT_TYPE =
            UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    static final UUID RULE =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MaintenanceScheduleContentTestFixtures() {
    }

    static MaintenanceScheduleCalculationContent canonicalContent() {
        return canonicalContent(
                " Annual Plan ",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                DEPARTMENT,
                List.of(EQUIPMENT_B, EQUIPMENT_A),
                Set.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
                MaintenanceScheduleRecurrenceAnchor.SHIFTED_DATE,
                item("a".repeat(64), new BigDecimal("1.00"), PriorityLevel.HIGH)
        );
    }

    static MaintenanceScheduleCalculationContent canonicalContent(
            String planName,
            LocalDate periodStart,
            LocalDate periodEnd,
            UUID departmentId,
            List<UUID> equipmentIds,
            Set<DayOfWeek> excludedWeekdays,
            MaintenanceScheduleRecurrenceAnchor recurrenceAnchor,
            MaintenanceScheduleCalculationItemContent item) {
        return new MaintenanceScheduleCalculationContent(
                planName,
                null,
                periodStart,
                periodEnd,
                MaintenanceScheduleScopeType.EQUIPMENT,
                PprScopeType.DEPARTMENT,
                departmentId,
                equipmentIds,
                List.of(EQUIPMENT_TYPE),
                List.of(REGULATION),
                MaintenanceScheduleAnchorMode.RESET_TO_PLAN_START,
                recurrenceAnchor,
                true,
                excludedWeekdays,
                1L,
                List.of(item)
        );
    }

    static MaintenanceScheduleCalculationItemContent item(
            String sourceItemKey,
            BigDecimal laborHours,
            PriorityLevel priority) {
        return new MaintenanceScheduleCalculationItemContent(
                sourceItemKey,
                1,
                EQUIPMENT_A,
                REGULATION,
                RULE,
                null,
                MaintenanceKind.PREVENTIVE,
                MaintenanceTriggerPolicy.ANY,
                " calendar ",
                2L,
                LocalDate.of(2026, 2, 3),
                LocalDateTime.of(2026, 2, 3, 8, 30, 0, 123_456_789),
                null,
                LocalDateTime.of(2026, 2, 4, 8, 30),
                laborHours,
                priority,
                DEPARTMENT,
                " Inspect pump "
        );
    }
}
