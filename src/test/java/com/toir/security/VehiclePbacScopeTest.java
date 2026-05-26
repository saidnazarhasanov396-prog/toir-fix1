package com.toir.security;

import com.toir.controller.VehicleController;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VehiclePbacScopeTest {

    @Mock
    VehicleService service;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    EquipmentRepository equipmentRepository;

    MockMvc mockMvc;
    UUID departmentId;

    @BeforeEach
    void setUp() {
        departmentId = UUID.randomUUID();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VehicleController(service, scopeAccessService, equipmentRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void vehicleListRespectsDepartmentScope() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(departmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(departmentId);
        when(service.list(eq(departmentId), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/v1/vehicles").param("departmentId", requestedDepartmentId.toString()))
                .andExpect(status().isOk());

        verify(service).list(eq(departmentId), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void vehicleDetailDeniedForOtherDepartment() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(vehicleEquipment(equipmentId, departmentId)));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}", equipmentId))
                .andExpect(status().isForbidden());

        verify(service, never()).findByEquipmentId(equipmentId);
    }

    @Test
    void vehicleUpdateDeniedForOtherDepartment() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(vehicleEquipment(equipmentId, departmentId)));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(put("/api/v1/vehicles/{equipmentId}", equipmentId)
                        .contentType("application/json")
                        .content(vehiclePayload(departmentId)))
                .andExpect(status().isForbidden());

        verify(service, never()).update(eq(equipmentId), any());
    }

    @Test
    void vehicleDeleteDeniedForOtherDepartment() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(vehicleEquipment(equipmentId, departmentId)));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(delete("/api/v1/vehicles/{equipmentId}", equipmentId))
                .andExpect(status().isForbidden());

        verify(service, never()).delete(equipmentId);
    }

    @Test
    void vehicleCreateDeniedForUnauthorizedDepartment() throws Exception {
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType("application/json")
                        .content(vehiclePayload(departmentId)))
                .andExpect(status().isForbidden());

        verify(service, never()).create(any());
    }

    @Test
    void vehicleAdminCanAccessAllDepartments() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(vehicleEquipment(equipmentId, departmentId)));
        when(service.findByEquipmentId(equipmentId))
                .thenReturn(new VehicleDetailDto(null, null, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/vehicles/{equipmentId}", equipmentId))
                .andExpect(status().isOk());

        verify(service).findByEquipmentId(equipmentId);
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
