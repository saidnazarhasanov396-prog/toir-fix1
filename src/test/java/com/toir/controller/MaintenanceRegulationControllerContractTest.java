package com.toir.controller;

import com.toir.controller.maintenance.MaintenanceRegulationController;
import com.toir.dto.maintenanceregulation.EquipmentWithRegulationsDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationAttributeConditionDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationSummaryDto;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MeterType;
import com.toir.enums.MaintenanceRegulationConditionOperator;
import com.toir.enums.PeriodicityUnit;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.maintanance.MaintenanceRegulationService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Year;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MaintenanceRegulationControllerContractTest {

    @Mock
    MaintenanceRegulationService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MaintenanceRegulationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listPassesEquipmentTypeAndActiveFiltersToService() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        when(service.search(0, 20, "pump", equipmentTypeId, true, "PREVENTIVE"))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/maintenance-regulations")
                        .param("equipmentTypeId", equipmentTypeId.toString())
                        .param("active", "true")
                        .param("category", "PREVENTIVE")
                        .param("search", "pump"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void equipmentWithRegulationsReturnsEquipmentAndSummaries() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        EquipmentWithRegulationsDto dto = new EquipmentWithRegulationsDto(
                equipmentId,
                "Pump A",
                "EQ-2026-0001",
                equipmentTypeId,
                "Pump",
                List.of(new MaintenanceRegulationSummaryDto(
                        regulationId,
                        "MR-2026-0001",
                        "Monthly pump regulation",
                        "PREVENTIVE",
                        3,
                        "Mechanic",
                        "Lockout",
                        "Wrench",
                        "Seal kit",
                        "Grease",
                        true
                ))
        );
        when(service.equipmentWithRegulations(equipmentTypeId, true, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/maintenance-regulations/equipment")
                        .param("equipmentTypeId", equipmentTypeId.toString())
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentTypeName").value("Pump"))
                .andExpect(jsonPath("$.content[0].regulations[0].id").value(regulationId.toString()))
                .andExpect(jsonPath("$.content[0].regulations[0].requiredSkill").value("Mechanic"));
    }

    @Test
    void equipmentWithRegulationsSupportsUnpagedWrapper() throws Exception {
        when(service.equipmentWithRegulations(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/maintenance-regulations/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void createWithoutCodeReturnsGeneratedCode() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        String generatedCode = "MR-" + Year.now().getValue() + "-0001";
        MaintenanceRegulationDto dto = new MaintenanceRegulationDto(
                id,
                generatedCode,
                "Monthly pump regulation",
                "Regulation description",
                equipmentTypeId,
                "Pump",
                null,
                null,
                null,
                MaintenanceKind.PREVENTIVE,
                3.0,
                true,
                PeriodicityUnit.MONTH,
                1,
                3,
                false,
                MeterType.CUSTOM,
                10.0
        );
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/maintenance-regulations")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Monthly pump regulation",
                                  "description": "Regulation description",
                                  "equipmentTypeId": "%s",
                                  "maintenanceKind": "PREVENTIVE",
                                  "normativeLaborHours": 3.0,
                                  "active": true,
                                  "periodicityUnit": "MONTH",
                                  "periodicityValue": 1,
                                  "toleranceDays": 3,
                                  "requiresShutdown": false,
                                  "triggerMeterType": "CUSTOM",
                                  "triggerMeterInterval": 10.0
                                }
                                """.formatted(equipmentTypeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value(generatedCode));
    }

    @Test
    void createWithCodeReturnsBadRequest() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        when(service.create(any())).thenThrow(RestException.badRequest(
                "code is generated by backend and must not be provided"));

        mockMvc.perform(post("/api/v1/maintenance-regulations")
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "MR-2026-0099",
                                  "name": "Monthly pump regulation",
                                  "description": "Regulation description",
                                  "equipmentTypeId": "%s",
                                  "maintenanceKind": "PREVENTIVE",
                                  "normativeLaborHours": 3.0,
                                  "active": true,
                                  "periodicityUnit": "MONTH",
                                  "periodicityValue": 1,
                                  "toleranceDays": 3,
                                  "requiresShutdown": false,
                                  "triggerMeterType": "CUSTOM",
                                  "triggerMeterInterval": 10.0
                                }
                                """.formatted(equipmentTypeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("code is generated by backend and must not be provided"));
    }

    @Test
    void createWithAttributeConditionsReturnsConditions() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID conditionId = UUID.randomUUID();
        String generatedCode = "MR-" + Year.now().getValue() + "-0001";
        MaintenanceRegulationDto dto = new MaintenanceRegulationDto(
                id,
                generatedCode,
                "High-power pump regulation",
                "Only pumps above 50 kW",
                equipmentTypeId,
                "Pump",
                null,
                null,
                null,
                MaintenanceKind.PREVENTIVE,
                4.0,
                true,
                PeriodicityUnit.MONTH,
                1,
                3,
                false,
                MeterType.CUSTOM,
                10.0,
                List.of(new MaintenanceRegulationAttributeConditionDto(
                        conditionId,
                        id,
                        "motor_power",
                        MaintenanceRegulationConditionOperator.GREATER_THAN,
                        null,
                        50.0,
                        null,
                        null,
                        null
                ))
        );
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/maintenance-regulations")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "High-power pump regulation",
                                  "description": "Only pumps above 50 kW",
                                  "equipmentTypeId": "%s",
                                  "maintenanceKind": "PREVENTIVE",
                                  "normativeLaborHours": 4.0,
                                  "active": true,
                                  "periodicityUnit": "MONTH",
                                  "periodicityValue": 1,
                                  "toleranceDays": 3,
                                  "requiresShutdown": false,
                                  "triggerMeterType": "CUSTOM",
                                  "triggerMeterInterval": 10.0,
                                  "attributeConditions": [
                                    {
                                      "attributeKey": "motor_power",
                                      "operator": "GREATER_THAN",
                                      "valueNumber": 50
                                    }
                                  ]
                                }
                                """.formatted(equipmentTypeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attributeConditions[0].attributeKey").value("motor_power"))
                .andExpect(jsonPath("$.attributeConditions[0].operator").value("GREATER_THAN"))
                .andExpect(jsonPath("$.attributeConditions[0].valueNumber").value(50.0));
    }

    @Test
    void updateWithCodeReturnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        when(service.update(eq(id), any())).thenThrow(RestException.badRequest(
                "code is generated by backend and must not be provided"));

        mockMvc.perform(put("/api/v1/maintenance-regulations/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "MR-2026-0012",
                                  "name": "Monthly pump regulation",
                                  "description": "Regulation description",
                                  "equipmentTypeId": "%s",
                                  "maintenanceKind": "PREVENTIVE",
                                  "normativeLaborHours": 3.0,
                                  "active": true,
                                  "periodicityUnit": "MONTH",
                                  "periodicityValue": 1,
                                  "toleranceDays": 3,
                                  "requiresShutdown": false,
                                  "triggerMeterType": "CUSTOM",
                                  "triggerMeterInterval": 10.0
                                }
                                """.formatted(equipmentTypeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("code is generated by backend and must not be provided"));
    }
}
