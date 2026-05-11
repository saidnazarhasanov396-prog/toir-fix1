package com.toir.controller;

import com.toir.controller.equipment.EquipmentScanCompatibilityController;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentScanControllerContractTest {

    @Mock
    EquipmentRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EquipmentScanCompatibilityController(repository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void scanExistingEquipmentReturns200StableObject() throws Exception {
        Equipment equipment = equipment(UUID.randomUUID());
        when(repository.findByIdAndIsDeletedFalse(equipment.getId())).thenReturn(Optional.of(equipment));

        mockMvc.perform(get("/api/v1/scan/equipment/{id}", equipment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(equipment.getId().toString()))
                .andExpect(jsonPath("$.code").value(equipment.getCode()))
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.matches.length()").value(1));
    }

    @Test
    void scanUnknownEquipmentReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/scan/equipment/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void scanInvalidUuidReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/scan/equipment/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-0001");
        equipment.setName("Compressor A");
        equipment.setInventoryNumber("INV-200");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }
}

