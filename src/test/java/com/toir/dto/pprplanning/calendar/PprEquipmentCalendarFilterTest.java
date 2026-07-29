package com.toir.dto.pprplanning.calendar;

import com.toir.enums.MaintenanceKind;
import com.toir.enums.PprTaskStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PprEquipmentCalendarFilterTest {

    @Test
    void defaultsPagingAndWorkFlags() {
        PprEquipmentCalendarFilter filter = new PprEquipmentCalendarFilter(
                2026, null, null, null, null, null, null, null, null, null, null);

        assertThat(filter.year()).isEqualTo(2026);
        assertThat(filter.page()).isZero();
        assertThat(filter.size()).isEqualTo(25);
        assertThat(filter.maintenanceKinds()).isEmpty();
        assertThat(filter.taskStatuses()).isEmpty();
        assertThat(filter.onlyWithWork()).isTrue();
        assertThat(filter.includeCancelled()).isFalse();
    }

    @Test
    void preservesExplicitBindingValuesAndNormalizesSearch() {
        UUID departmentId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID locationId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID equipmentId = UUID.fromString("30000000-0000-0000-0000-000000000001");

        PprEquipmentCalendarFilter filter = new PprEquipmentCalendarFilter(
                Integer.valueOf(2026),
                Integer.valueOf(2),
                Integer.valueOf(100),
                " Pump ",
                departmentId,
                locationId,
                equipmentId,
                Set.of(MaintenanceKind.INSPECTION),
                Set.of(PprTaskStatus.PLANNED),
                Boolean.FALSE,
                Boolean.TRUE);

        assertThat(filter.year()).isEqualTo(2026);
        assertThat(filter.page()).isEqualTo(2);
        assertThat(filter.size()).isEqualTo(100);
        assertThat(filter.search()).isEqualTo("Pump");
        assertThat(filter.departmentId()).isEqualTo(departmentId);
        assertThat(filter.locationId()).isEqualTo(locationId);
        assertThat(filter.equipmentId()).isEqualTo(equipmentId);
        assertThat(filter.maintenanceKinds()).containsExactly(MaintenanceKind.INSPECTION);
        assertThat(filter.taskStatuses()).containsExactly(PprTaskStatus.PLANNED);
        assertThat(filter.onlyWithWork()).isFalse();
        assertThat(filter.includeCancelled()).isTrue();
    }

    @Test
    void acceptsTheInclusivePagingBoundaries() {
        assertThat(new PprEquipmentCalendarFilter(
                2026, 0, 1, null, null, null, null, null, null, false, true).size()).isEqualTo(1);
        assertThat(new PprEquipmentCalendarFilter(
                2026, 0, 100, null, null, null, null, null, null, false, true).size()).isEqualTo(100);
    }

    @Test
    void rejectsInvalidPagingValues() {
        assertThatThrownBy(() -> new PprEquipmentCalendarFilter(
                2026, -1, 25, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be greater than or equal to 0");
        assertThatThrownBy(() -> new PprEquipmentCalendarFilter(
                2026, 0, 0, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and 100");
        assertThatThrownBy(() -> new PprEquipmentCalendarFilter(
                2026, 0, 101, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and 100");
    }
}
