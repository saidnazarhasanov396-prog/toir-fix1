package com.toir.security;

import com.toir.controller.equipment.EquipmentAttributeController;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.equipment.EquipmentAttributeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EquipmentAttributeController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        EquipmentAttributeControllerSecurityTest.SecurityBeans.class
})
class EquipmentAttributeControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    EquipmentAttributeService service;

    @MockBean
    ScopeAccessService scopeAccessService;

    @MockBean
    EquipmentRepository equipmentRepository;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return properties;
        }
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void equipmentAttributeReadRequiresEquipmentScope() throws Exception {
        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void equipmentAttributeWriteRequiresEquipmentScope() throws Exception {
        mockMvc.perform(put("/api/v1/equipment/{equipmentId}/attributes", UUID.randomUUID())
                        .contentType("application/json")
                        .content("[]"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void equipmentAttributeHistoryRequiresEquipmentScope() throws Exception {
        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes/history", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_READ)
    void equipmentReadCanReadAttributeValuesAndHistory() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, UUID.randomUUID())));
        when(service.findValues(equipmentId)).thenReturn(List.of());
        when(service.findValueHistory(eq(equipmentId), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes", equipmentId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes/history", equipmentId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_UPDATE)
    void equipmentUpdateCanWriteAttributeValues() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, UUID.randomUUID())));
        when(service.replaceValues(eq(equipmentId), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/attributes", equipmentId)
                        .contentType("application/json")
                        .content("[]"))
                .andExpect(status().isOk());
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
}
