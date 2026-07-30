package com.toir.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarExcludedDiagnostics;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarFilter;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarPageMetadata;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarPlacementBasis;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarPlanSummary;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarResponse;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarSpanMode;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarSortDirection;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarSortField;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.pprcalendar.PprEquipmentCalendarService;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PprEquipmentCalendarControllerTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Mock PprEquipmentCalendarService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PprEquipmentCalendarController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsMultiValueFiltersAndDefaultsToDedicatedService() throws Exception {
        when(service.getCalendar(eq(PLAN_ID), any())).thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/ppr-plans/{planId}/equipment-calendar", PLAN_ID)
                        .param("year", "2026")
                        .param("month", "7")
                        .param("search", "pump")
                        .param("equipmentTypeId", "50000000-0000-0000-0000-000000000001")
                        .param("sortBy", "INVENTORY_NUMBER")
                        .param("sortDirection", "DESC")
                        .param("maintenanceKinds", "INSPECTION", "OVERHAUL")
                        .param("statuses", "PLANNED", "IN_PROGRESS"))
                .andExpect(status().isOk());

        ArgumentCaptor<PprEquipmentCalendarFilter> filterCaptor =
                ArgumentCaptor.forClass(PprEquipmentCalendarFilter.class);
        verify(service).getCalendar(eq(PLAN_ID), filterCaptor.capture());
        assertThat(filterCaptor.getValue().page()).isZero();
        assertThat(filterCaptor.getValue().month()).isEqualTo(7);
        assertThat(filterCaptor.getValue().size()).isEqualTo(25);
        assertThat(filterCaptor.getValue().onlyWithWork()).isTrue();
        assertThat(filterCaptor.getValue().includeCancelled()).isFalse();
        assertThat(filterCaptor.getValue().equipmentTypeId()).isEqualTo(
                UUID.fromString("50000000-0000-0000-0000-000000000001"));
        assertThat(filterCaptor.getValue().sortBy())
                .isEqualTo(PprEquipmentCalendarSortField.INVENTORY_NUMBER);
        assertThat(filterCaptor.getValue().sortDirection())
                .isEqualTo(PprEquipmentCalendarSortDirection.DESC);
        assertThat(filterCaptor.getValue().maintenanceKinds())
                .containsExactlyInAnyOrder(MaintenanceKind.INSPECTION, MaintenanceKind.OVERHAUL);
        assertThat(filterCaptor.getValue().taskStatuses())
                .containsExactlyInAnyOrder(PprTaskStatus.PLANNED, PprTaskStatus.IN_PROGRESS);
    }

    @Test
    void convertsSizeAboveOneHundredAndInvalidYearToBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/ppr-plans/{planId}/equipment-calendar", PLAN_ID)
                        .param("year", "2026")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/ppr-plans/{planId}/equipment-calendar", PLAN_ID)
                        .param("year", "10000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointRequiresPprCalendarReadSystemAdminOrWildcard() throws Exception {
        Method endpoint = PprEquipmentCalendarController.class.getMethod(
                "getEquipmentCalendar",
                UUID.class,
                Integer.class,
                Integer.class,
                Integer.class,
                Integer.class,
                String.class,
                UUID.class,
                UUID.class,
                UUID.class,
                UUID.class,
                Set.class,
                Set.class,
                Boolean.class,
                Boolean.class,
                PprEquipmentCalendarSortField.class,
                PprEquipmentCalendarSortDirection.class);

        assertThat(endpoint.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyAuthority('PPR_CALENDAR_READ','SYSTEM_ADMIN','*')");
    }

    private PprEquipmentCalendarResponse emptyResponse() {
        return new PprEquipmentCalendarResponse(
                new PprEquipmentCalendarPlanSummary(
                        PLAN_ID,
                        "PPR-2026",
                        "Year plan",
                        PlanStatus.APPROVED,
                        null,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31)),
                2026,
                PprEquipmentCalendarAuthoritativeSource.PPR_TASK,
                null,
                PprEquipmentCalendarPlacementBasis.PLANNED_DATE,
                PprEquipmentCalendarSpanMode.START_MONTH,
                new PprEquipmentCalendarPageMetadata(0, 25, 0, 0),
                List.of(),
                new PprEquipmentCalendarExcludedDiagnostics(0, 0, 0, 0));
    }
}
