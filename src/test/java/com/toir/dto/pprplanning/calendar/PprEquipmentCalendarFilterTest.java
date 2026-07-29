package com.toir.dto.pprplanning.calendar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PprEquipmentCalendarFilterTest {

    @Test
    void defaultsPagingAndWorkFlags() {
        PprEquipmentCalendarFilter filter = new PprEquipmentCalendarFilter(
                2026, null, null, null, null, null, null, null, null, null, null);

        assertThat(filter.page()).isZero();
        assertThat(filter.size()).isEqualTo(25);
        assertThat(filter.onlyWithWork()).isTrue();
        assertThat(filter.includeCancelled()).isFalse();
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
