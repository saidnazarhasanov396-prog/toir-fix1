package com.toir.security;

import com.toir.controller.equipment.EquipmentAttributeController;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.equipment.EquipmentAttributeService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentAttributePbacScopeTest {

    @Mock
    EquipmentAttributeService service;

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
                .standaloneSetup(new EquipmentAttributeController(service, scopeAccessService, equipmentRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void equipmentAttributeReadRequiresEquipmentScope() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        when(service.findValues(equipmentId)).thenReturn(List.of(value(equipmentId)));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes", equipmentId))
                .andExpect(status().isOk());

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
    }

    @Test
    void equipmentAttributeWriteRequiresEquipmentScope() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        when(service.replaceValues(eq(equipmentId), any())).thenReturn(List.of(value(equipmentId)));

        mockMvc.perform(put("/api/v1/equipment/{equipmentId}/attributes", equipmentId)
                        .contentType("application/json")
                        .content("[]"))
                .andExpect(status().isOk());

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
    }

    @Test
    void equipmentAttributeHistoryRequiresEquipmentScope() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        when(service.findValueHistory(eq(equipmentId), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes/history", equipmentId))
                .andExpect(status().isOk());

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
    }

    @Test
    void equipmentAttributeReadDeniedForOtherDepartment() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes", equipmentId))
                .andExpect(status().isForbidden());

        verify(service, never()).findValues(equipmentId);
    }

    @Test
    void equipmentAttributeWriteDeniedForOtherDepartment() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/attributes", equipmentId)
                        .contentType("application/json")
                        .content("[]"))
                .andExpect(status().isForbidden());

        verify(service, never()).replaceValues(eq(equipmentId), any());
    }

    @Test
    void equipmentAttributeAdminCanAccessOtherDepartment() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        when(service.findValues(equipmentId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/attributes", equipmentId))
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

    private EquipmentAttributeValueDto value(UUID equipmentId) {
        return new EquipmentAttributeValueDto(
                UUID.randomUUID(),
                equipmentId,
                UUID.randomUUID(),
                "payload_capacity",
                "Payload capacity",
                null,
                null,
                EquipmentAttributeDataType.NUMBER,
                "kg",
                false,
                null,
                List.of(),
                "vehicle_metrics",
                10,
                null,
                12000.0,
                null,
                null,
                null,
                null
        );
    }
}
