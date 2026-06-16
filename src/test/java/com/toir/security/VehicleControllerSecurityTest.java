package com.toir.security;

import com.toir.controller.VehicleController;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleStatsResponse;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.VehicleDrivingSessionService;
import com.toir.service.VehiclePictureService;
import com.toir.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VehicleController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        VehicleControllerSecurityTest.SecurityBeans.class
})
class VehicleControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    VehicleService vehicleService;

    @MockBean
    VehiclePictureService vehiclePictureService;

    @MockBean
    VehicleDrivingSessionService vehicleDrivingSessionService;

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
    void vehicleListRequiresReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void vehicleDetailRequiresReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void vehicleCreateRequiresCreatePermission() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiclePayload(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void vehicleUpdateRequiresUpdatePermission() throws Exception {
        mockMvc.perform(put("/api/v1/vehicles/{equipmentId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiclePayload(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void vehicleDeleteRequiresDeletePermission() throws Exception {
        mockMvc.perform(delete("/api/v1/vehicles/{equipmentId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_READ)
    void equipmentReadCanReadVehicleListDetailAndStats() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        when(vehicleService.list(isNull(), isNull(), isNull(), isNull(), eq(0), eq(1))).thenReturn(Page.empty());
        when(vehicleService.getStats(isNull(), isNull())).thenReturn(new VehicleStatsResponse(0, 0, 0, 0));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(vehicleEquipment(equipmentId, UUID.randomUUID())));
        when(vehicleService.findByEquipmentId(equipmentId))
                .thenReturn(new VehicleDetailDto(null, null, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/vehicles?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/vehicles/stats"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}", equipmentId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_CREATE)
    void equipmentCreateCanCreateVehicle() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(vehicleService.create(any())).thenReturn(new VehicleDetailDto(null, null, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiclePayload(departmentId)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_UPDATE)
    void equipmentUpdateCanUpdateVehicle() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(vehicleEquipment(equipmentId, departmentId)));
        when(vehicleService.update(eq(equipmentId), any())).thenReturn(new VehicleDetailDto(null, null, List.of(), List.of()));

        mockMvc.perform(put("/api/v1/vehicles/{equipmentId}", equipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiclePayload(departmentId)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_DELETE)
    void equipmentDeleteCanDeleteVehicle() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(vehicleEquipment(equipmentId, UUID.randomUUID())));

        mockMvc.perform(delete("/api/v1/vehicles/{equipmentId}", equipmentId))
                .andExpect(status().isNoContent());
    }

    private Equipment vehicleEquipment(UUID equipmentId, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("VH-2026-0001");
        equipment.setName("Truck");
        equipment.setInventoryNumber("INV-1");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.VEHICLE);
        return equipment;
    }

    private String vehiclePayload(UUID departmentId) {
        return """
                {
                  "code": "VH-2026-0001",
                  "name": "Truck",
                  "inventoryNumber": "INV-1",
                  "equipmentTypeId": "%s",
                  "departmentId": "%s",
                  "status": "ACTIVE",
                  "plateNumber": "01A123AA",
                  "vehicleType": "TRUCK"
                }
                """.formatted(UUID.randomUUID(), departmentId);
    }
}
