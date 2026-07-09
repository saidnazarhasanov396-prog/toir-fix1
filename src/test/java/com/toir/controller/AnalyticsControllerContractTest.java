package com.toir.controller;

import com.toir.service.AnalyticsService;
import com.toir.service.ReliabilityPassportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerContractTest {

    @Mock
    AnalyticsService service;

    @Mock
    ReliabilityPassportService reliabilityPassportService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AnalyticsController(service, reliabilityPassportService))
                .build();
    }

    @Test
    void downtimeEventsPassesDepartmentAndPaginationToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(service.downtimeEvents(departmentId, 2, 15))
                .thenReturn(Page.empty(PageRequest.of(2, 15)));

        mockMvc.perform(get("/api/v1/analytics/downtime-events")
                        .param("departmentId", departmentId.toString())
                        .param("page", "2")
                        .param("size", "15"))
                .andExpect(status().isOk());

        verify(service).downtimeEvents(departmentId, 2, 15);
    }
}
