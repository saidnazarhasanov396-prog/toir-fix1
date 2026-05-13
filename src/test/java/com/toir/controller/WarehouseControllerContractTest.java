package com.toir.controller;

import com.toir.dto.warehouse.WarehouseEquipmentItemDto;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseControllerContractTest {

    @Mock
    WarehouseService warehouseService;

    @Mock
    WarehouseEquipmentItemService warehouseEquipmentItemService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WarehouseController(warehouseService, warehouseEquipmentItemService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void patchInstallWithoutDepartmentIdReturnsBadRequest() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(warehouseEquipmentItemService.updateStatus(
                eq(warehouseId),
                eq(equipmentId),
                eq(WarehouseEquipmentStatus.INSTALLED),
                isNull()
        )).thenThrow(RestException.badRequest("departmentId is required when status is INSTALLED"));

        mockMvc.perform(patch("/api/v1/warehouses/{warehouseId}/equipment/{equipmentId}/status", warehouseId, equipmentId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "INSTALLED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("departmentId is required when status is INSTALLED"));
    }

    @Test
    void patchInstallWithInvalidDepartmentIdReturnsNotFound() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(warehouseEquipmentItemService.updateStatus(
                eq(warehouseId),
                eq(equipmentId),
                eq(WarehouseEquipmentStatus.INSTALLED),
                eq(departmentId)
        )).thenThrow(RestException.notFound("Department not found: " + departmentId));

        mockMvc.perform(patch("/api/v1/warehouses/{warehouseId}/equipment/{equipmentId}/status", warehouseId, equipmentId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "INSTALLED",
                                  "departmentId": "%s"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department not found: " + departmentId));
    }

    @Test
    void patchInstallWithValidDepartmentIdReturnsOk() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WarehouseEquipmentItemDto response = new WarehouseEquipmentItemDto(
                UUID.randomUUID(),
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.INSTALLED,
                true,
                Instant.now()
        );
        when(warehouseEquipmentItemService.updateStatus(
                eq(warehouseId),
                eq(equipmentId),
                eq(WarehouseEquipmentStatus.INSTALLED),
                eq(departmentId)
        )).thenReturn(response);

        mockMvc.perform(patch("/api/v1/warehouses/{warehouseId}/equipment/{equipmentId}/status", warehouseId, equipmentId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "INSTALLED",
                                  "departmentId": "%s"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.status").value("INSTALLED"));
    }

    @Test
    void patchNonInstalledWithDepartmentIdReturnsBadRequest() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(warehouseEquipmentItemService.updateStatus(
                eq(warehouseId),
                eq(equipmentId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                eq(departmentId)
        )).thenThrow(RestException.badRequest("departmentId must be null when status is not INSTALLED"));

        mockMvc.perform(patch("/api/v1/warehouses/{warehouseId}/equipment/{equipmentId}/status", warehouseId, equipmentId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "AVAILABLE",
                                  "departmentId": "%s"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("departmentId must be null when status is not INSTALLED"));
    }

    @Test
    void patchAvailableWithoutDepartmentIdReturnsOk() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WarehouseEquipmentItemDto response = new WarehouseEquipmentItemDto(
                UUID.randomUUID(),
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.AVAILABLE,
                true,
                Instant.now()
        );
        when(warehouseEquipmentItemService.updateStatus(
                eq(warehouseId),
                eq(equipmentId),
                eq(WarehouseEquipmentStatus.AVAILABLE),
                isNull()
        )).thenReturn(response);

        mockMvc.perform(patch("/api/v1/warehouses/{warehouseId}/equipment/{equipmentId}/status", warehouseId, equipmentId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "AVAILABLE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }
}
