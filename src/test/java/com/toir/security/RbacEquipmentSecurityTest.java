package com.toir.security;

import com.toir.controller.equipment.EquipmentController;
import com.toir.controller.equipment.EquipmentLabelController;
import com.toir.controller.equipment.EquipmentScanCompatibilityController;
import com.toir.dto.equipment.EquipmentDetailDto;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipment.EquipmentStatsResponse;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.EquipmentUsageSessionService;
import com.toir.service.equipment.EquipmentPictureService;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        EquipmentController.class,
        EquipmentLabelController.class,
        EquipmentScanCompatibilityController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacEquipmentSecurityTest.SecurityBeans.class
})
class RbacEquipmentSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    EquipmentService equipmentService;

    @MockBean
    EquipmentStatusLifecycleService equipmentStatusLifecycleService;

    @MockBean
    EquipmentPictureService equipmentPictureService;

    @MockBean
    EquipmentUsageSessionService equipmentUsageSessionService;

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
    void unauthenticatedCannotReadEquipment() throws Exception {
        mockMvc.perform(get("/api/v1/equipment?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadEquipment() throws Exception {
        mockMvc.perform(get("/api/v1/equipment?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_READ)
    void equipmentReadCanReadListDetailStatsChildrenAndScan() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(equipmentId);
        Equipment entity = equipmentEntity(equipmentId);

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        when(equipmentService.search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(1)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 1), 1));
        when(equipmentService.findDetailById(equipmentId)).thenReturn(new EquipmentDetailDto(dto, List.of(), List.of(), List.of(), List.of()));
        when(equipmentService.getEquipmentStats(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new EquipmentStatsResponse(1, 1, 0, 0));
        when(equipmentService.findChildren(equipmentId, 0, 1))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(entity));
        when(equipmentRepository.findByCodeAndIsDeletedFalse("EQ-2026-0001")).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/v1/equipment?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment/{id}", equipmentId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment/stats"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment/{id}/children?page=0&size=1", equipmentId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment/{id}/label", equipmentId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment/by-code/{code}", "EQ-2026-0001"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment/resolve-scan").param("payload", equipmentId.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/scan/equipment/{id}", equipmentId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadEquipment() throws Exception {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        when(equipmentService.search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(1)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        mockMvc.perform(get("/api/v1/equipment?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadEquipment() throws Exception {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        when(equipmentService.search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(1)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        mockMvc.perform(get("/api/v1/equipment?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_CREATE)
    void equipmentCreateCanCreateEquipment() throws Exception {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(equipmentService.create(any())).thenReturn(equipmentDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_UPDATE)
    void equipmentUpdateCanUpdateEquipment() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipmentEntity(equipmentId)));
        when(equipmentService.update(eq(equipmentId), any())).thenReturn(equipmentDto(equipmentId));

        mockMvc.perform(put("/api/v1/equipment/{id}", equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_TRANSFER)
    void equipmentTransferCanUpdatePlacement() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipmentEntity(equipmentId)));
        when(equipmentService.updatePlacement(eq(equipmentId), any())).thenReturn(equipmentDto(equipmentId));

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "DEPARTMENT",
                                  "departmentId": "%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_DELETE)
    void equipmentDeleteCanDeleteEquipment() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipmentEntity(equipmentId)));

        mockMvc.perform(delete("/api/v1/equipment/{id}", equipmentId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_READ)
    void equipmentReadCannotMutateEquipment() throws Exception {
        UUID equipmentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/equipment/{id}", equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "DEPARTMENT",
                                  "departmentId": "%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/equipment/{id}", equipmentId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotMutateEquipment() throws Exception {
        mockMvc.perform(post("/api/v1/equipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotMutateEquipment() throws Exception {
        mockMvc.perform(post("/api/v1/equipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isForbidden());
    }

    private EquipmentDto equipmentDto(UUID id) {
        UUID equipmentTypeId = UUID.randomUUID();
        return new EquipmentDto(
                id,
                "EQ-2026-0001",
                "Pump A",
                "INV-1",
                null,
                null,
                null,
                equipmentTypeId,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Equipment equipmentEntity(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-0001");
        equipment.setName("Pump A");
        equipment.setInventoryNumber("INV-1");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private String createPayload() {
        return """
                {
                  "name": "Pump A",
                  "inventoryNumber": "INV-1",
                  "equipmentTypeId": "%s",
                  "expectedLifetimeHours": 10000,
                  "departmentId": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private String updatePayload() {
        return """
                {
                  "name": "Pump A",
                  "inventoryNumber": "INV-1",
                  "equipmentTypeId": "%s",
                  "status": "ACTIVE"
                }
                """.formatted(UUID.randomUUID());
    }
}
