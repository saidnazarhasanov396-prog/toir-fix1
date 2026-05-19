package com.toir.controller;

import com.toir.dto.warehouse.WarehouseEquipmentItemDto;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void getWarehouseEquipmentReturnsPagedItems() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WarehouseEquipmentItemDto item = new WarehouseEquipmentItemDto(
                itemId,
                warehouseId,
                equipmentId,
                WarehouseEquipmentStatus.AVAILABLE,
                true,
                Instant.now()
        );
        Page<WarehouseEquipmentItemDto> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
        when(warehouseEquipmentItemService.list(
                eq(warehouseId),
                isNull(),
                eq(0),
                eq(20)
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/equipment", warehouseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.content[0].warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getWarehouseEquipmentSanitizesPageAndSizeAndForwardsStatusFilter() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseEquipmentItemService.list(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.OUT_OF_SERVICE),
                eq(0),
                eq(1)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        MvcResult result = mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/equipment", warehouseId)
                        .param("status", "OUT_OF_SERVICE")
                        .param("page", "-5")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andReturn();

        assertThat(result.getResolvedException()).isNull();

        verify(warehouseEquipmentItemService).list(
                eq(warehouseId),
                eq(WarehouseEquipmentStatus.OUT_OF_SERVICE),
                eq(0),
                eq(1)
        );
    }
}
