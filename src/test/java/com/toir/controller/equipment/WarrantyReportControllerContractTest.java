package com.toir.controller.equipment;

import com.toir.dto.equipment.WarrantyReportItem;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.equipment.WarrantyReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarrantyReportControllerContractTest {

    @Mock
    WarrantyReportService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WarrantyReportController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithoutFiltersReturnsPagedWarrantyReport() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        LocalDate end = LocalDate.of(2026, 12, 31);
        WarrantyReportItem item = new WarrantyReportItem(
                equipmentId,
                "PUMP-001",
                "Main Pump",
                "Maintenance",
                true,
                LocalDate.of(2025, 1, 1),
                end,
                "ACTIVE",
                180,
                supplierId,
                "KSB Service"
        );

        when(service.report(isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment/warranty-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentCode").value("PUMP-001"))
                .andExpect(jsonPath("$.content[0].equipmentName").value("Main Pump"))
                .andExpect(jsonPath("$.content[0].departmentName").value("Maintenance"))
                .andExpect(jsonPath("$.content[0].hasWarranty").value(true))
                .andExpect(jsonPath("$.content[0].warrantyEndDate[0]").value(2026))
                .andExpect(jsonPath("$.content[0].warrantyEndDate[1]").value(12))
                .andExpect(jsonPath("$.content[0].warrantyEndDate[2]").value(31))
                .andExpect(jsonPath("$.content[0].warrantyStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].daysUntilExpiry").value(180))
                .andExpect(jsonPath("$.content[0].warrantySupplierId").value(supplierId.toString()))
                .andExpect(jsonPath("$.content[0].warrantySupplierName").value("KSB Service"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listWithFiltersPassesParametersToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();

        when(service.report(
                eq(departmentId),
                eq("EXPIRING_30"),
                eq(supplierId),
                eq("pump"),
                eq(1),
                eq(10)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 10), 0));

        mockMvc.perform(get("/api/v1/equipment/warranty-report")
                        .param("departmentId", departmentId.toString())
                        .param("status", "EXPIRING_30")
                        .param("supplierId", supplierId.toString())
                        .param("search", "pump")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(service).report(departmentId, "EXPIRING_30", supplierId, "pump", 1, 10);
    }
}
