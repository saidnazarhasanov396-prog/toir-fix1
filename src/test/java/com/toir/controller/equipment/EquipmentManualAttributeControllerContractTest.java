package com.toir.controller.equipment;

import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeDto;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.equipment.EquipmentManualAttributeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentManualAttributeControllerContractTest {

    @Mock
    EquipmentManualAttributeService service;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    VehicleDetailsRepository vehicleDetailsRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EquipmentManualAttributeController(
                        service,
                        equipmentRepository,
                        vehicleDetailsRepository,
                        scopeAccessService
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void manualAttributeWriteEndpointCreatesAttribute() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(service.create(eq(equipmentId), any())).thenReturn(new EquipmentManualAttributeDto(
                attributeId,
                "legacy_key",
                "legacy value"
        ));

        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/manual-attributes", equipmentId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "key": "legacy_key",
                                  "value": "legacy value"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(attributeId.toString()))
                .andExpect(jsonPath("$.key").value("legacy_key"))
                .andExpect(jsonPath("$.value").value("legacy value"));

        verify(service).create(eq(equipmentId), any());
    }

    @Test
    void manualAttributeReadEndpointRemainsReadOnlyIfKept() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(service.list(equipmentId)).thenReturn(List.of(new EquipmentManualAttributeDto(
                attributeId,
                "legacy_key",
                "legacy value"
        )));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/manual-attributes", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attributeId.toString()))
                .andExpect(jsonPath("$[0].key").value("legacy_key"))
                .andExpect(jsonPath("$[0].value").value("legacy value"));
    }

    private static Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-001");
        equipment.setName("Compressor");
        equipment.setInventoryNumber("INV-001");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }
}
