package com.toir.controller;

import com.toir.controller.equipment.EquipmentLabelController;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
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
class EquipmentLabelControllerContractTest {

    @Mock
    EquipmentRepository repository;

    @Mock
    ScopeAccessService scopeAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EquipmentLabelController(repository, scopeAccessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void labelForExistingEquipmentReturns200StableObject() throws Exception {
        Equipment equipment = equipment(UUID.randomUUID());
        when(repository.findByIdAndIsDeletedFalse(equipment.getId())).thenReturn(Optional.of(equipment));

        mockMvc.perform(get("/api/v1/equipment/{id}/label", equipment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment.id").value(equipment.getId().toString()))
                .andExpect(jsonPath("$.equipmentId").value(equipment.getId().toString()))
                .andExpect(jsonPath("$.code").value(equipment.getCode()))
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.matches.length()").value(1));
    }

    @Test
    void resolveScanWithToirPayloadReturns200() throws Exception {
        Equipment equipment = equipment(UUID.randomUUID());
        when(repository.findByIdAndIsDeletedFalse(equipment.getId())).thenReturn(Optional.of(equipment));

        mockMvc.perform(get("/api/v1/equipment/resolve-scan")
                        .param("payload", "toir://equipment/" + equipment.getId() + "?code=" + equipment.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(equipment.getId().toString()))
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.matches.length()").value(1));
    }

    @Test
    void labelForUnknownEquipmentReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/equipment/{id}/label", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-0001");
        equipment.setName("Pump A");
        equipment.setInventoryNumber("INV-100");
        equipment.setTechnicalNumber(null);
        equipment.setSerialNumber(null);
        equipment.setModel(null);
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }
}
