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
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentPbacScopeTest {

    @Mock
    EquipmentService service;

    @Mock
    EquipmentRepository repository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    EquipmentStatusLifecycleService statusLifecycleService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EquipmentController(service, repository, scopeAccessService, statusLifecycleService),
                        new EquipmentLabelController(repository, scopeAccessService),
                        new EquipmentScanCompatibilityController(repository, scopeAccessService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listClampsRequestedDepartmentToCurrentUserDepartment() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(currentDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(service.search(eq(currentDepartmentId), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/v1/equipment").param("departmentId", requestedDepartmentId.toString()))
                .andExpect(status().isOk());

        verify(service).search(eq(currentDepartmentId), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(20));
    }

    @Test
    void listWithoutCurrentDepartmentIsDeniedForNonAdmin() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);

        mockMvc.perform(get("/api/v1/equipment"))
                .andExpect(status().isForbidden());

        verify(service, never()).search(any(), any(), any(), any(), any(), eq(false), any(), anyInt(), anyInt());
    }

    @Test
    void adminCanRequestGlobalList() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(service.search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/v1/equipment"))
                .andExpect(status().isOk());

        verify(service).search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(20));
    }

    @Test
    void statsUseClampedDepartment() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(currentDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(service.getEquipmentStats(null, null, currentDepartmentId, null))
                .thenReturn(new EquipmentStatsResponse(1, 1, 0, 0));

        mockMvc.perform(get("/api/v1/equipment/stats").param("departmentId", requestedDepartmentId.toString()))
                .andExpect(status().isOk());

        verify(service).getEquipmentStats(null, null, currentDepartmentId, null);
    }

    @Test
    void detailAllowsEquipmentInScope() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment(id, departmentId);
        EquipmentDto dto = equipmentDto(id, departmentId);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment));
        when(service.findDetailById(id)).thenReturn(new EquipmentDetailDto(dto, List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/equipment/{id}", id))
                .andExpect(status().isOk());

        verify(scopeAccessService, atLeastOnce()).assertCanAccessDepartment(departmentId);
    }

    @Test
    void detailDeniesEquipmentOutOfScope() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment(id, departmentId);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(get("/api/v1/equipment/{id}", id))
                .andExpect(status().isForbidden());

        verify(service, never()).findDetailById(id);
    }

    @Test
    void missingEquipmentStillReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/equipment/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAllowsOwnDepartment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(service.create(any())).thenReturn(equipmentDto(id, departmentId));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content(createPayload(departmentId, null)))
                .andExpect(status().isCreated());

        verify(scopeAccessService, atLeastOnce()).assertCanAccessDepartment(departmentId);
    }

    @Test
    void createDeniesOtherDepartment() throws Exception {
        UUID departmentId = UUID.randomUUID();
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content(createPayload(departmentId, null)))
                .andExpect(status().isForbidden());

        verify(service, never()).create(any());
    }

    @Test
    void createWarehouseOnlyRemainsCompatible() throws Exception {
        UUID id = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(service.create(any())).thenReturn(equipmentDto(id, null));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content(createPayload(null, warehouseId)))
                .andExpect(status().isCreated());

        verify(scopeAccessService, never()).assertCanAccessWarehouse(any());
    }

    @Test
    void updateChecksExistingEquipmentDepartmentAndTargetDepartment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment(id, departmentId);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment));
        when(service.update(eq(id), any())).thenReturn(equipmentDto(id, departmentId));

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content(updatePayload(departmentId)))
                .andExpect(status().isOk());

        verify(scopeAccessService, atLeastOnce()).assertCanAccessDepartment(departmentId);
    }

    @Test
    void updateDeniesExistingEquipmentOutOfScope() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment(id, departmentId);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content(updatePayload(null)))
                .andExpect(status().isForbidden());

        verify(service, never()).update(eq(id), any());
    }

    @Test
    void deleteChecksExistingEquipmentDepartment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment(id, departmentId)));

        mockMvc.perform(delete("/api/v1/equipment/{id}", id))
                .andExpect(status().isNoContent());

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
        verify(service).delete(id);
    }

    @Test
    void placementDeniesCrossDepartmentTransferForNonAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sourceDepartmentId = UUID.randomUUID();
        UUID targetDepartmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment(id, sourceDepartmentId)));
        doNothing().when(scopeAccessService).assertCanAccessDepartment(sourceDepartmentId);
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(targetDepartmentId);

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "DEPARTMENT",
                                  "departmentId": "%s"
                                }
                                """.formatted(targetDepartmentId)))
                .andExpect(status().isForbidden());

        verify(service, never()).updatePlacement(eq(id), any());
    }

    @Test
    void placementAllowsAdminCrossDepartmentTransfer() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sourceDepartmentId = UUID.randomUUID();
        UUID targetDepartmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment(id, sourceDepartmentId)));
        when(service.updatePlacement(eq(id), any())).thenReturn(equipmentDto(id, targetDepartmentId));

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "DEPARTMENT",
                                  "departmentId": "%s"
                                }
                                """.formatted(targetDepartmentId)))
                .andExpect(status().isOk());
    }

    @Test
    void labelReadChecksEquipmentDepartment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment(id, departmentId)));

        mockMvc.perform(get("/api/v1/equipment/{id}/label", id))
                .andExpect(status().isOk());

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
    }

    @Test
    void scanReadDeniesOutOfScopeEquipment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(equipment(id, departmentId)));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(get("/api/v1/scan/equipment/{id}", id))
                .andExpect(status().isForbidden());
    }

    private Equipment equipment(UUID id, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-0001");
        equipment.setName("Pump A");
        equipment.setInventoryNumber("INV-1");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private EquipmentDto equipmentDto(UUID id, UUID departmentId) {
        return new EquipmentDto(
                id,
                "EQ-2026-0001",
                "Pump A",
                "INV-1",
                null,
                null,
                null,
                UUID.randomUUID(),
                departmentId,
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

    private String createPayload(UUID departmentId, UUID warehouseId) {
        String department = departmentId == null ? "" : """
                  "departmentId": "%s",
                """.formatted(departmentId);
        String warehouse = warehouseId == null ? "" : """
                  "warehouseId": "%s",
                """.formatted(warehouseId);
        return """
                {
                  "name": "Pump A",
                  "inventoryNumber": "INV-1",
                  "equipmentTypeId": "%s",
                %s%s  "status": "ACTIVE"
                }
                """.formatted(UUID.randomUUID(), department, warehouse);
    }

    private String updatePayload(UUID departmentId) {
        String department = departmentId == null ? "" : """
                  "departmentId": "%s",
                """.formatted(departmentId);
        return """
                {
                  "name": "Pump A",
                  "inventoryNumber": "INV-1",
                %s  "status": "ACTIVE"
                }
                """.formatted(department);
    }
}
