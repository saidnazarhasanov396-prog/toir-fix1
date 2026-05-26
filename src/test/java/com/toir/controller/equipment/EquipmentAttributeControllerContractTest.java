package com.toir.controller.equipment;

import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeOptionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueHistoryDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.uom.UnitOfMeasurementDto;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.enums.EquipmentAttributeValueHistorySource;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.equipment.EquipmentAttributeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentAttributeControllerContractTest {

    @Mock
    EquipmentAttributeService service;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    EquipmentRepository equipmentRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EquipmentAttributeController(service, scopeAccessService, equipmentRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(equipmentRepository.findByIdAndIsDeletedFalse(any()))
                .thenAnswer(invocation -> Optional.of(equipment(invocation.getArgument(0), UUID.randomUUID())));
    }

    private Equipment equipment(UUID equipmentId, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("EQ-2026-0001");
        equipment.setName("Pump A");
        equipment.setInventoryNumber("INV-1");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    @Test
    void returnsCanonicalDefinitionShape() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        UUID optionSourceId = UUID.randomUUID();
        when(service.findDefinitions(equipmentTypeId)).thenReturn(List.of(new EquipmentAttributeDefinitionDto(
                attributeId,
                equipmentTypeId,
                "seal_type",
                "Seal Type",
                "Тип уплотнения",
                "Muhr turi",
                EquipmentAttributeDataType.SELECT,
                null,
                true,
                null,
                null,
                optionSourceId,
                List.of(new EquipmentAttributeOptionDto("mechanical", "Mechanical", "Механическое", "Mexanik", 10, true)),
                "Pump",
                20,
                List.of()
        )));

        mockMvc.perform(get("/api/v1/equipment-types/{equipmentTypeId}/attributes", equipmentTypeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attributeId.toString()))
                .andExpect(jsonPath("$[0].equipmentTypeId").value(equipmentTypeId.toString()))
                .andExpect(jsonPath("$[0].key").value("seal_type"))
                .andExpect(jsonPath("$[0].label").value("Seal Type"))
                .andExpect(jsonPath("$[0].dataType").value("SELECT"))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[0].optionSourceId").value(optionSourceId.toString()))
                .andExpect(jsonPath("$[0].options[0].id").value("mechanical"))
                .andExpect(jsonPath("$[0].groupName").value("Pump"))
                .andExpect(jsonPath("$[0].sortOrder").value(20));
    }

    @Test
    void createsDefinitionWithOptionSourceIdAndOptions() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        UUID optionSourceId = UUID.randomUUID();
        when(service.createDefinition(eq(equipmentTypeId), any())).thenReturn(new EquipmentAttributeDefinitionDto(
                attributeId,
                equipmentTypeId,
                "seal_type",
                "Seal Type",
                null,
                null,
                EquipmentAttributeDataType.SELECT,
                null,
                true,
                null,
                null,
                optionSourceId,
                List.of(new EquipmentAttributeOptionDto("mechanical", "Mechanical", null, null, 10, true)),
                "Pump",
                20,
                List.of()
        ));

        mockMvc.perform(post("/api/v1/equipment-types/{equipmentTypeId}/attributes", equipmentTypeId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "key": "seal_type",
                                  "label": "Seal Type",
                                  "dataType": "SELECT",
                                  "required": true,
                                  "optionSourceId": "%s",
                                  "options": [
                                    {
                                      "id": "mechanical",
                                      "label": "Mechanical",
                                      "sortOrder": 10,
                                      "active": true
                                    }
                                  ],
                                  "groupName": "Pump",
                                  "sortOrder": 20
                                }
                                """.formatted(optionSourceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(attributeId.toString()))
                .andExpect(jsonPath("$.optionSourceId").value(optionSourceId.toString()))
                .andExpect(jsonPath("$.options[0].id").value("mechanical"));

        ArgumentCaptor<com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest.class);
        verify(service).createDefinition(eq(equipmentTypeId), captor.capture());
        assertThat(captor.getValue().optionSourceId()).isEqualTo(optionSourceId);
        assertThat(captor.getValue().options()).extracting(EquipmentAttributeOptionDto::id)
                .containsExactly("mechanical");
    }

    @Test
    void createsDefinitionsBatch() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID firstAttributeId = UUID.randomUUID();
        UUID secondAttributeId = UUID.randomUUID();
        when(service.createDefinitionsBatch(eq(equipmentTypeId), any())).thenReturn(List.of(
                new EquipmentAttributeDefinitionDto(
                        firstAttributeId,
                        equipmentTypeId,
                        "motor_power",
                        "Motor Power",
                        null,
                        null,
                        EquipmentAttributeDataType.NUMBER,
                        new UnitOfMeasurementDto(UUID.randomUUID(), "UOM-2026-0001", "kW", "Kilowatt", "Kilovatt"),
                        true,
                        0.0,
                        500.0,
                        null,
                        List.of(),
                        "Motor",
                        10,
                        List.of()
                ),
                new EquipmentAttributeDefinitionDto(
                        secondAttributeId,
                        equipmentTypeId,
                        "seal_type",
                        "Seal Type",
                        null,
                        null,
                        EquipmentAttributeDataType.TEXT,
                        null,
                        false,
                        null,
                        null,
                        null,
                        List.of(),
                        "Pump",
                        20,
                        List.of()
                )
        ));

        mockMvc.perform(post("/api/v1/equipment-types/{equipmentTypeId}/attributes/batch", equipmentTypeId)
                        .contentType("application/json")
                        .content("""
                                [
                                  {
                                    "key": "Motor_Power",
                                    "label": "Motor Power",
                                    "dataType": "NUMBER",
                                    "unit": "kW",
                                    "required": true,
                                    "minValue": 0,
                                    "maxValue": 500,
                                    "groupName": "Motor",
                                    "sortOrder": 10
                                  },
                                  {
                                    "key": "seal_type",
                                    "label": "Seal Type",
                                    "dataType": "TEXT",
                                    "required": false,
                                    "groupName": "Pump",
                                    "sortOrder": 20
                                  }
                                ]
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(firstAttributeId.toString()))
                .andExpect(jsonPath("$[0].key").value("motor_power"))
                .andExpect(jsonPath("$[0].unit.code").value("UOM-2026-0001"))
                .andExpect(jsonPath("$[1].id").value(secondAttributeId.toString()))
                .andExpect(jsonPath("$[1].key").value("seal_type"))
                .andExpect(jsonPath("$[1].unit").doesNotExist());

        ArgumentCaptor<List<com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(service).createDefinitionsBatch(eq(equipmentTypeId), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().getFirst().key()).isEqualTo("Motor_Power");
        assertThat(captor.getValue().getLast().key()).isEqualTo("seal_type");
    }

    @Test
    void batchCreateRejectsInvalidBody() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/equipment-types/{equipmentTypeId}/attributes/batch", equipmentTypeId)
                        .contentType("application/json")
                        .content("""
                                [
                                  {
                                    "key": "",
                                    "label": "Motor Power",
                                    "dataType": "NUMBER"
                                  }
                                ]
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void batchCreateRejectsNullBody() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/equipment-types/{equipmentTypeId}/attributes/batch", equipmentTypeId)
                        .contentType("application/json")
                        .content("null"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void batchCreateRejectsEmptyList() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/equipment-types/{equipmentTypeId}/attributes/batch", equipmentTypeId)
                        .contentType("application/json")
                        .content("[]"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void batchCreateRejectsNullItemInsideList() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/equipment-types/{equipmentTypeId}/attributes/batch", equipmentTypeId)
                        .contentType("application/json")
                        .content("[null]"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void batchCreateUnknownEquipmentTypeReturnsNotFound() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        when(service.createDefinitionsBatch(eq(equipmentTypeId), any()))
                .thenThrow(RestException.notFound("Equipment type not found: " + equipmentTypeId));

        mockMvc.perform(post("/api/v1/equipment-types/{equipmentTypeId}/attributes/batch", equipmentTypeId)
                        .contentType("application/json")
                        .content("""
                                [
                                  {
                                    "key": "motor_power",
                                    "label": "Motor Power",
                                    "dataType": "NUMBER"
                                  }
                                ]
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Equipment type not found: " + equipmentTypeId));
    }

    @Test
    void acceptsValidTypedValuesAsDirectListPayload() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        when(service.replaceValues(eq(equipmentId), any())).thenReturn(List.of(new EquipmentAttributeValueDto(
                UUID.randomUUID(),
                equipmentId,
                attributeId,
                "motor_power",
                "Motor Power",
                null,
                null,
                EquipmentAttributeDataType.NUMBER,
                "kW",
                true,
                null,
                List.of(),
                "Motor",
                10,
                null,
                75.0,
                null,
                null,
                null,
                null
        )));

        mockMvc.perform(put("/api/v1/equipment/{equipmentId}/attributes", equipmentId)
                        .contentType("application/json")
                        .content("""
                                [
                                  {
                                    "attributeDefinitionId": "%s",
                                    "key": "motor_power",
                                    "valueNumber": 75
                                  }
                                ]
                                """.formatted(attributeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attributeDefinitionId").value(attributeId.toString()))
                .andExpect(jsonPath("$[0].valueNumber").value(75.0));
    }

    @Test
    void rejectsStaleWrappedValuePayloadShape() throws Exception {
        UUID equipmentId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/equipment/{equipmentId}/attributes", equipmentId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "values": [
                                    {
                                      "attributeId": "attr-1",
                                      "value": "75"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDefinition_acceptsRequiredForCriticalityClassIds() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        UUID criticalityId = UUID.randomUUID();
        when(service.createDefinition(eq(equipmentTypeId), any())).thenReturn(new EquipmentAttributeDefinitionDto(
                attributeId,
                equipmentTypeId,
                "vibration_limit",
                "Vibration Limit",
                null,
                null,
                EquipmentAttributeDataType.NUMBER,
                new UnitOfMeasurementDto(UUID.randomUUID(), "UOM-2026-0002", "mm/s", "Millimeters per second", null),
                false,
                null,
                null,
                null,
                List.of(),
                "Monitoring",
                10,
                List.of(criticalityId)
        ));

        mockMvc.perform(post("/api/v1/equipment-types/{equipmentTypeId}/attributes", equipmentTypeId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "key": "vibration_limit",
                                  "label": "Vibration Limit",
                                  "dataType": "NUMBER",
                                  "required": false,
                                  "requiredForCriticalityClassIds": ["%s"],
                                  "groupName": "Monitoring",
                                  "sortOrder": 10
                                }
                                """.formatted(criticalityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiredForCriticalityClassIds[0]").value(criticalityId.toString()));

        ArgumentCaptor<com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest.class);
        verify(service).createDefinition(eq(equipmentTypeId), captor.capture());
        assertThat(captor.getValue().requiredForCriticalityClassIds()).containsExactly(criticalityId);
    }

    @Test
    void getDefinitions_returnsRequiredForCriticalityClassIds() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        UUID criticalityId = UUID.randomUUID();
        when(service.findDefinitions(equipmentTypeId)).thenReturn(List.of(new EquipmentAttributeDefinitionDto(
                attributeId,
                equipmentTypeId,
                "vibration_limit",
                "Vibration Limit",
                null,
                null,
                EquipmentAttributeDataType.NUMBER,
                new UnitOfMeasurementDto(UUID.randomUUID(), "UOM-2026-0002", "mm/s", "Millimeters per second", null),
                false,
                null,
                null,
                null,
                List.of(),
                "Monitoring",
                10,
                List.of(criticalityId)
        )));

        mockMvc.perform(get("/api/v1/equipment-types/{equipmentTypeId}/attributes", equipmentTypeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requiredForCriticalityClassIds[0]").value(criticalityId.toString()));
    }

    @Test
    void updateDefinition_replacesRequiredForCriticalityClassIds() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        UUID criticalityId = UUID.randomUUID();
        when(service.updateDefinition(eq(equipmentTypeId), eq(attributeId), any())).thenReturn(new EquipmentAttributeDefinitionDto(
                attributeId,
                equipmentTypeId,
                "vibration_limit",
                "Vibration Limit",
                null,
                null,
                EquipmentAttributeDataType.NUMBER,
                new UnitOfMeasurementDto(UUID.randomUUID(), "UOM-2026-0002", "mm/s", "Millimeters per second", null),
                false,
                null,
                null,
                null,
                List.of(),
                "Monitoring",
                10,
                List.of(criticalityId)
        ));

        mockMvc.perform(put("/api/v1/equipment-types/{equipmentTypeId}/attributes/{attributeId}", equipmentTypeId, attributeId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "key": "vibration_limit",
                                  "label": "Vibration Limit",
                                  "dataType": "NUMBER",
                                  "required": false,
                                  "requiredForCriticalityClassIds": ["%s"]
                                }
                                """.formatted(criticalityId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredForCriticalityClassIds[0]").value(criticalityId.toString()));

        ArgumentCaptor<com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest.class);
        verify(service).updateDefinition(eq(equipmentTypeId), eq(attributeId), captor.capture());
        assertThat(captor.getValue().requiredForCriticalityClassIds()).containsExactly(criticalityId);
    }

    @Test
    void upsertValues_missingCriticalityRequiredAttribute_returnsBadRequest() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.replaceValues(eq(equipmentId), any()))
                .thenThrow(RestException.badRequest("Missing required equipment attributes: vibration_limit (required by criticality)"));

        mockMvc.perform(put("/api/v1/equipment/{equipmentId}/attributes", equipmentId)
                        .contentType("application/json")
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required equipment attributes: vibration_limit (required by criticality)"));
    }

    @Test
    void getAttributeValueHistory_returnsPage() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID historyId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        when(service.findValueHistory(eq(equipmentId), eq(null), any())).thenReturn(new PageImpl<>(
                List.of(new EquipmentAttributeValueHistoryDto(
                        historyId,
                        equipmentId,
                        attributeId,
                        "motor_power",
                        "Motor Power",
                        null,
                        "75",
                        null,
                        Instant.parse("2026-05-23T06:00:00Z"),
                        EquipmentAttributeValueHistorySource.API,
                        null
                )),
                PageRequest.of(0, 20),
                1
        ));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes/history", equipmentId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(historyId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].attributeDefinitionId").value(attributeId.toString()))
                .andExpect(jsonPath("$.content[0].attributeKey").value("motor_power"))
                .andExpect(jsonPath("$.content[0].oldValue").doesNotExist())
                .andExpect(jsonPath("$.content[0].newValue").value("75"))
                .andExpect(jsonPath("$.content[0].source").value("API"));
    }

    @Test
    void getAttributeValueHistory_filtersByAttributeDefinitionId() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        when(service.findValueHistory(eq(equipmentId), eq(attributeId), any())).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0
        ));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes/history", equipmentId)
                        .param("attributeDefinitionId", attributeId.toString()))
                .andExpect(status().isOk());

        verify(service).findValueHistory(eq(equipmentId), eq(attributeId), any());
    }

    @Test
    void upsertValues_recordsHistory() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.replaceValues(eq(equipmentId), any())).thenReturn(List.of());

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
                .andExpect(status().isOk());

        verify(service).replaceValues(eq(equipmentId), any());
    }
}
