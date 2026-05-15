package com.toir.controller;

import com.toir.dto.calibration.CalibrationRecordDto;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.CalibrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CalibrationControllerContractTest {

    @Mock
    CalibrationService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CalibrationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithEquipmentIdReturnsOnlyThatEquipment() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.findForEquipment(equipmentId)).thenReturn(List.of(
                dto(UUID.randomUUID(), equipmentId, "CERT-AB1"),
                dto(UUID.randomUUID(), equipmentId, "CERT-AB2")
        ));

        mockMvc.perform(get("/api/v1/calibration-records")
                        .param("equipmentId", equipmentId.toString())
                        .param("search", "ignored")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[1].equipmentId").value(equipmentId.toString()));

        verify(service).findForEquipment(equipmentId);
        verify(service, never()).findAll(anyString());
    }

    @Test
    void listWithUnknownEquipmentIdReturns404() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.findForEquipment(equipmentId))
                .thenThrow(RestException.notFound("Equipment not found: " + equipmentId));

        mockMvc.perform(get("/api/v1/calibration-records")
                        .param("equipmentId", equipmentId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Equipment not found: " + equipmentId));
    }

    @Test
    void createWithValidCertificateNumberReturnsCreated() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        when(service.create(any())).thenReturn(dto(recordId, equipmentId, "CERT-2026-001"));

        mockMvc.perform(post("/api/v1/calibration-records")
                        .contentType("application/json")
                        .content("""
                                {
                                  "equipmentId":"%s",
                                  "certificateNumber":"CERT-2026-001",
                                  "performedAt":"2026-05-01"
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(recordId.toString()))
                .andExpect(jsonPath("$.certificateNumber").value("CERT-2026-001"));
    }

    @Test
    void createWithBlankCertificateNumberReturns400() throws Exception {
        UUID equipmentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/calibration-records")
                        .contentType("application/json")
                        .content("""
                                {
                                  "equipmentId":"%s",
                                  "certificateNumber":"   ",
                                  "performedAt":"2026-05-01"
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("certificateNumber")));
    }

    @Test
    void createWithTooShortCertificateNumberReturns400() throws Exception {
        UUID equipmentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/calibration-records")
                        .contentType("application/json")
                        .content("""
                                {
                                  "equipmentId":"%s",
                                  "certificateNumber":"A1",
                                  "performedAt":"2026-05-01"
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("certificateNumber")));
    }

    @Test
    void createWithInvalidCertificateNumberReturns400() throws Exception {
        UUID equipmentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/calibration-records")
                        .contentType("application/json")
                        .content("""
                                {
                                  "equipmentId":"%s",
                                  "certificateNumber":"AB#12",
                                  "performedAt":"2026-05-01"
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("certificateNumber")));
    }

    @Test
    void updateWithInvalidCertificateNumberReturns400() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/calibration-records/{id}", recordId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "equipmentId":"%s",
                                  "certificateNumber":"bir",
                                  "performedAt":"2026-05-01"
                                }
                                """.formatted(equipmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("certificateNumber")));

        verify(service, never()).update(eq(recordId), any());
    }

    private CalibrationRecordDto dto(UUID id, UUID equipmentId, String certificateNumber) {
        return new CalibrationRecordDto(
                id,
                equipmentId,
                certificateNumber,
                "Lab",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 11, 1),
                "PASS",
                null,
                null,
                "%",
                null,
                null,
                0
        );
    }
}
