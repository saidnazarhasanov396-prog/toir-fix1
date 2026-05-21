package com.toir.controller;

import com.toir.controller.equipment.EquipmentAttributeController;
import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeOptionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeOptionSourceDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.equipment.EquipmentAttributeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentAttributeControllerContractTest {

    @Mock
    EquipmentAttributeService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EquipmentAttributeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listDefinitionsReturnsEquipmentTypeAttributes() throws Exception {
        UUID typeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        when(service.findDefinitions(typeId)).thenReturn(List.of(definitionDto(typeId, definitionId, "motor_power")));

        mockMvc.perform(get("/api/v1/equipment-types/{equipmentTypeId}/attributes", typeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(definitionId.toString()))
                .andExpect(jsonPath("$[0].key").value("motor_power"))
                .andExpect(jsonPath("$[0].dataType").value("NUMBER"))
                .andExpect(jsonPath("$[0].unit").value("kW"))
                .andExpect(jsonPath("$[0].required").value(true));
    }

    @Test
    void createOptionSourceAndReplaceOptionsContracts() throws Exception {
        UUID sourceId = UUID.randomUUID();
        when(service.createOptionSource(any())).thenReturn(new EquipmentAttributeOptionSourceDto(
                sourceId,
                "seal_types",
                "Seal Types",
                null,
                null,
                "Pump seal options"
        ));
        when(service.replaceOptions(eq(sourceId), any())).thenReturn(List.of(
                new EquipmentAttributeOptionDto("mechanical_seal", "Mechanical seal", null, null, 10, true)
        ));

        mockMvc.perform(post("/api/v1/equipment-attribute-option-sources")
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "seal_types",
                                  "name": "Seal Types",
                                  "description": "Pump seal options"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(sourceId.toString()))
                .andExpect(jsonPath("$.code").value("seal_types"));

        mockMvc.perform(put("/api/v1/equipment-attribute-option-sources/{sourceId}/options", sourceId)
                        .contentType("application/json")
                        .content("""
                                [
                                  {
                                    "id": "mechanical_seal",
                                    "label": "Mechanical seal",
                                    "sortOrder": 10,
                                    "active": true
                                  }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("mechanical_seal"))
                .andExpect(jsonPath("$[0].label").value("Mechanical seal"));
    }

    @Test
    void createDefinitionReturnsCreatedDefinition() throws Exception {
        UUID typeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        when(service.createDefinition(eq(typeId), any())).thenReturn(definitionDto(typeId, definitionId, "motor_power"));

        mockMvc.perform(post("/api/v1/equipment-types/{equipmentTypeId}/attributes", typeId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "key": "motor_power",
                                  "label": "Motor Power",
                                  "labelRu": "Мощность двигателя",
                                  "labelUz": "Dvigatel quvvati",
                                  "dataType": "NUMBER",
                                  "unit": "kW",
                                  "required": true,
                                  "minValue": 0,
                                  "maxValue": 500,
                                  "groupName": "Motor",
                                  "sortOrder": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(definitionId.toString()))
                .andExpect(jsonPath("$.key").value("motor_power"));
    }

    @Test
    void updateDefinitionReturnsUpdatedDefinition() throws Exception {
        UUID typeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        when(service.updateDefinition(eq(typeId), eq(definitionId), any()))
                .thenReturn(definitionDto(typeId, definitionId, "motor_power"));

        mockMvc.perform(put("/api/v1/equipment-types/{equipmentTypeId}/attributes/{attributeId}", typeId, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "key": "motor_power",
                                  "label": "Motor Power",
                                  "dataType": "NUMBER",
                                  "unit": "kW",
                                  "required": true,
                                  "minValue": 0,
                                  "maxValue": 500,
                                  "sortOrder": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(definitionId.toString()))
                .andExpect(jsonPath("$.sortOrder").value(10));
    }

    @Test
    void deleteDefinitionReturnsNoContent() throws Exception {
        UUID typeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/equipment-types/{equipmentTypeId}/attributes/{attributeId}", typeId, definitionId))
                .andExpect(status().isNoContent());

        verify(service).deleteDefinition(typeId, definitionId);
    }

    @Test
    void listValuesReturnsEquipmentAttributes() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        when(service.findValues(equipmentId)).thenReturn(List.of(valueDto(equipmentId, definitionId, 75.0)));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$[0].attributeDefinitionId").value(definitionId.toString()))
                .andExpect(jsonPath("$[0].key").value("motor_power"))
                .andExpect(jsonPath("$[0].valueNumber").value(75.0));
    }

    @Test
    void replaceValuesAcceptsKeyAndDefinitionIdPayloads() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        when(service.replaceValues(eq(equipmentId), any())).thenReturn(List.of(valueDto(equipmentId, definitionId, 90.0)));

        mockMvc.perform(put("/api/v1/equipment/{equipmentId}/attributes", equipmentId)
                        .contentType("application/json")
                        .content("""
                                [
                                  {
                                    "key": "motor_power",
                                    "valueNumber": 90
                                  },
                                  {
                                    "attributeDefinitionId": "%s",
                                    "valueOption": "mechanical_seal"
                                  }
                                ]
                                """.formatted(definitionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].valueNumber").value(90.0));
    }

    @Test
    void saveValuesPostAcceptsReusableAttributeValuesPayload() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        when(service.replaceValues(eq(equipmentId), any())).thenReturn(List.of(valueDto(equipmentId, definitionId, 75.0)));

        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/attributes", equipmentId)
                        .contentType("application/json")
                        .content("""
                                [
                                  {
                                    "key": "motor_power",
                                    "valueNumber": 75
                                  }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$[0].key").value("motor_power"))
                .andExpect(jsonPath("$[0].valueNumber").value(75.0));
    }

    @Test
    void replaceValuesValidationErrorReturnsBadRequest() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.replaceValues(eq(equipmentId), any()))
                .thenThrow(RestException.badRequest("Unknown equipment attribute key: unknown_key"));

        mockMvc.perform(put("/api/v1/equipment/{equipmentId}/attributes", equipmentId)
                        .contentType("application/json")
                        .content("""
                                [
                                  {
                                    "key": "unknown_key",
                                    "valueText": "bad"
                                  }
                                ]
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown equipment attribute key: unknown_key"));
    }

    private EquipmentAttributeDefinitionDto definitionDto(UUID typeId, UUID definitionId, String key) {
        return new EquipmentAttributeDefinitionDto(
                definitionId,
                typeId,
                key,
                "Motor Power",
                "Мощность двигателя",
                "Dvigatel quvvati",
                EquipmentAttributeDataType.NUMBER,
                "kW",
                true,
                0.0,
                500.0,
                null,
                List.of(new EquipmentAttributeOptionDto("mechanical_seal", "Mechanical seal", null, null, 10, true)),
                "Motor",
                10
        );
    }

    private EquipmentAttributeValueDto valueDto(UUID equipmentId, UUID definitionId, Double value) {
        return new EquipmentAttributeValueDto(
                UUID.randomUUID(),
                equipmentId,
                definitionId,
                "motor_power",
                "Motor Power",
                "Мощность двигателя",
                "Dvigatel quvvati",
                EquipmentAttributeDataType.NUMBER,
                "kW",
                true,
                null,
                List.of(),
                "Motor",
                10,
                null,
                value,
                null,
                null,
                null,
                null
        );
    }
}
