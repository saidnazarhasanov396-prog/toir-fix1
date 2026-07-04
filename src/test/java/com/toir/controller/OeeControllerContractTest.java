package com.toir.controller;

import com.toir.dto.oee.OeeFilter;
import com.toir.dto.oee.OeeRecordDto;
import com.toir.dto.oee.OeeSummary;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.OeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OeeControllerContractTest {

    @Mock
    OeeService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OeeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithoutFiltersShouldReturnRecords() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(service.list(any(OeeFilter.class))).thenReturn(List.of(new OeeRecordDto(
                recordId,
                equipmentId,
                Instant.parse("2026-01-02T08:00:00Z"),
                Instant.parse("2026-01-02T16:00:00Z"),
                480,
                420,
                30,
                800,
                780,
                0.875,
                0.952,
                0.975,
                0.812,
                "day shift"
        )));

        mockMvc.perform(get("/api/v1/oee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(recordId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(service).list(argThat(filter -> "shiftStart".equals(filter.sortBy()) && "desc".equals(filter.sortDir())));
    }

    @Test
    void listShouldForwardExpandedFilters() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        when(service.list(any(OeeFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/oee")
                        .param("equipmentSearch", "compressor")
                        .param("departmentId", departmentId.toString())
                        .param("equipmentTypeId", equipmentTypeId.toString())
                        .param("minOee", "0.70")
                        .param("maxOee", "0.95")
                        .param("sortBy", "oee")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(service).list(argThat(filter ->
                "compressor".equals(filter.equipmentSearch())
                        && departmentId.equals(filter.departmentId())
                        && equipmentTypeId.equals(filter.equipmentTypeId())
                        && filter.minOee().equals(0.70)
                        && filter.maxOee().equals(0.95)
                        && "oee".equals(filter.sortBy())
                        && "asc".equals(filter.sortDir())
        ));
    }

    @Test
    void summaryShouldSupportEquipmentBusinessSearch() throws Exception {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        when(service.summary(any(OeeFilter.class)))
                .thenReturn(new OeeSummary(null, from, to, 0, 0, 0, 0, 0));

        mockMvc.perform(get("/api/v1/oee/summary")
                        .param("equipmentSearch", "compressor")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(0));

        verify(service).summary(argThat(filter ->
                "compressor".equals(filter.equipmentSearch())
                        && from.equals(filter.from())
                        && to.equals(filter.to())
        ));
    }

    @Test
    void listWithInvalidEquipmentIdShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/oee").param("equipmentId", "invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'equipmentId': invalid-uuid. Expected UUID."));

        verifyNoInteractions(service);
    }

    @Test
    void postShouldNotBeAvailableForOeeUi() throws Exception {
        mockMvc.perform(post("/api/v1/oee").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(service);
    }
}
