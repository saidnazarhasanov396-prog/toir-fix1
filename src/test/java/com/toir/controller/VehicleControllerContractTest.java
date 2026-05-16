package com.toir.controller;

import com.toir.dto.vehicle.VehicleStatsResponse;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.SecurityScope;
import com.toir.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VehicleControllerContractTest {

    @Mock
    VehicleService service;

    @Mock
    SecurityScope securityScope;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VehicleController(service, securityScope))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void statsWithoutFiltersReturnsVehicleStats() throws Exception {
        VehicleStatsResponse response = new VehicleStatsResponse(
                11,
                9,
                1,
                1
        );

        when(securityScope.enforceDepartmentScope(isNull())).thenReturn(null);
        when(service.getStats(null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/vehicles/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.active").value(9))
                .andExpect(jsonPath("$.inRepair").value(1))
                .andExpect(jsonPath("$.outOfService").value(1));

        verify(securityScope).enforceDepartmentScope(null);
        verify(service).getStats(null, null);
    }

    @Test
    void statsWithFiltersPassesScopedDepartmentAndSearchToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID scopedDepartmentId = departmentId;

        VehicleStatsResponse response = new VehicleStatsResponse(
                4,
                3,
                1,
                0
        );

        when(securityScope.enforceDepartmentScope(departmentId)).thenReturn(scopedDepartmentId);
        when(service.getStats(scopedDepartmentId, "kamaz")).thenReturn(response);

        mockMvc.perform(get("/api/v1/vehicles/stats")
                        .param("departmentId", departmentId.toString())
                        .param("search", "kamaz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.active").value(3))
                .andExpect(jsonPath("$.inRepair").value(1))
                .andExpect(jsonPath("$.outOfService").value(0));

        verify(securityScope).enforceDepartmentScope(departmentId);
        verify(service).getStats(scopedDepartmentId, "kamaz");
    }
}