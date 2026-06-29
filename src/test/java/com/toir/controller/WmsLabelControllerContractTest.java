package com.toir.controller;

import com.toir.dto.warehouse.WmsLabelPayloadDto;
import com.toir.dto.warehouse.WmsScanValidationResultDto;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.warehouse.WmsLabelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WmsLabelControllerContractTest {

    @Mock
    WmsLabelService service;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WmsLabelController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void labelRoutesReturnWmsPayloads() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        when(service.binLabel(binId)).thenReturn(new WmsLabelPayloadDto(
                "BIN",
                binId,
                "A-01-02",
                warehouseId,
                binId,
                null,
                null,
                "TOIR-WMS|type=BIN|id=%s|warehouseId=%s|code=A-01-02".formatted(binId, warehouseId)
        ));
        when(service.sparePartLabel(sparePartId)).thenReturn(new WmsLabelPayloadDto(
                "SPARE_PART",
                sparePartId,
                "SP-100",
                null,
                null,
                sparePartId,
                null,
                "TOIR-WMS|type=SPARE_PART|id=%s|code=SP-100".formatted(sparePartId)
        ));
        when(service.equipmentLabel(equipmentId)).thenReturn(new WmsLabelPayloadDto(
                "EQUIPMENT",
                equipmentId,
                "INV-7788",
                null,
                null,
                null,
                equipmentId,
                "TOIR-WMS|type=EQUIPMENT|id=%s|inventoryNumber=INV-7788".formatted(equipmentId)
        ));

        mockMvc.perform(get("/api/v1/warehouse/labels/bins/{binId}", binId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BIN"))
                .andExpect(jsonPath("$.warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.binId").value(binId.toString()))
                .andExpect(jsonPath("$.payload").value("TOIR-WMS|type=BIN|id=%s|warehouseId=%s|code=A-01-02"
                        .formatted(binId, warehouseId)));

        mockMvc.perform(get("/api/v1/warehouse/labels/spare-parts/{sparePartId}", sparePartId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SPARE_PART"))
                .andExpect(jsonPath("$.sparePartId").value(sparePartId.toString()));

        mockMvc.perform(get("/api/v1/warehouse/labels/equipment/{equipmentId}", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EQUIPMENT"))
                .andExpect(jsonPath("$.equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.code").value("INV-7788"));
    }

    @Test
    void scanValidationRouteReturnsValidationResult() throws Exception {
        UUID binId = UUID.randomUUID();
        when(service.validateScan(any())).thenReturn(new WmsScanValidationResultDto(
                true,
                "BIN",
                binId,
                "BIN",
                binId,
                null,
                "Scan is valid"
        ));

        mockMvc.perform(post("/api/v1/warehouse/scan/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedType": "BIN",
                                  "expectedId": "%s",
                                  "scannedPayload": "TOIR-WMS|type=BIN|id=%s|code=A-01-02"
                                }
                                """.formatted(binId, binId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.expectedType").value("BIN"))
                .andExpect(jsonPath("$.scannedId").value(binId.toString()));
    }
}
