package com.toir.controller;

import com.toir.controller.equipment.EquipmentController;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.security.SecurityScope;
import com.toir.service.equipment.EquipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentControllerContractTest {

    @Mock
    EquipmentService service;

    @Mock
    SecurityScope securityScope;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EquipmentController(service, securityScope))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createWithDepartmentIdOnlyReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId);
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-1",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value("EQ-2026-0020"));
    }

    @Test
    void createWithWarehouseIdOnlyReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null);
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-2",
                                  "equipmentTypeId": "%s",
                                  "warehouseId": "%s"
                                }
                                """.formatted(equipmentTypeId, warehouseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createWithBothDepartmentAndWarehouseReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId);
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-3",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s",
                                  "warehouseId": "%s"
                                }
                                """.formatted(equipmentTypeId, departmentId, warehouseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()));
    }

    @Test
    void createWithNeitherDepartmentNorWarehouseReturnsBadRequest() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        when(service.create(any())).thenThrow(RestException.badRequest("departmentId or warehouseId is required"));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-4",
                                  "equipmentTypeId": "%s"
                                }
                                """.formatted(equipmentTypeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("departmentId or warehouseId is required"));
    }

    @Test
    void createWithInvalidWarehouseIdReturnsNotFound() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(service.create(any())).thenThrow(RestException.notFound("Warehouse not found: " + warehouseId));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-5",
                                  "equipmentTypeId": "%s",
                                  "warehouseId": "%s"
                                }
                                """.formatted(equipmentTypeId, warehouseId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Warehouse not found: " + warehouseId));
    }

    @Test
    void createWithInvalidDepartmentIdReturnsNotFound() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(service.create(any())).thenThrow(RestException.notFound("Department not found: " + departmentId));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-6",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department not found: " + departmentId));
    }

    @Test
    void createWithClientProvidedCodeReturnsBadRequest() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(service.create(any())).thenThrow(RestException.badRequest("Equipment code is generated by system and must not be provided"));

        mockMvc.perform(post("/api/v1/equipment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "EQ-2026-0017",
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-7",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Equipment code is generated by system and must not be provided"));
    }

    @Test
    void updateWarehouseOnlyEquipmentWithoutDepartmentIdReturnsSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null);
        when(service.update(eq(id), any())).thenReturn(dto);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A Updated",
                                  "inventoryNumber": "INV-2",
                                  "equipmentTypeId": "%s"
                                }
                                """.formatted(equipmentTypeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value("EQ-2026-0020"));
    }

    @Test
    void updateWithDepartmentIdReturnsSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId);
        when(service.update(eq(id), any())).thenReturn(dto);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Compressor A Updated",
                                  "inventoryNumber": "INV-3",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()));
    }

    @Test
    void updateWithInvalidDepartmentIdReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(service.update(eq(id), any())).thenThrow(RestException.notFound("Department not found: " + departmentId));

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "departmentId": "%s"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department not found: " + departmentId));
    }

    @Test
    void updateWithClientProvidedCodeReturnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(service.update(eq(id), any())).thenThrow(RestException.badRequest("Equipment code is generated by system and must not be provided"));

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "EQ-2026-0017",
                                  "name": "Compressor A",
                                  "inventoryNumber": "INV-2",
                                  "equipmentTypeId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(equipmentTypeId, departmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Equipment code is generated by system and must not be provided"));
    }

    @Test
    void listWithLocationIdReturnsLocationObject() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        EquipmentDto.Ref locationRef = new EquipmentDto.Ref(locationId, "LOC-001", "Main Workshop");
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, locationId, locationRef);

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, null, null, false, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].locationId").value(locationId.toString()))
                .andExpect(jsonPath("$.content[0].location.id").value(locationId.toString()))
                .andExpect(jsonPath("$.content[0].location.code").value("LOC-001"))
                .andExpect(jsonPath("$.content[0].location.name").value("Main Workshop"));
    }

    @Test
    void listWithNullLocationIdReturnsLocationNull() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, null, null);

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, null, null, false, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].locationId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].location").value(nullValue()));
    }

    @Test
    void listWithMissingLocationRecordDoesNot500() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID missingLocationId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, missingLocationId, null);

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, null, null, false, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].locationId").value(missingLocationId.toString()))
                .andExpect(jsonPath("$.content[0].location").value(nullValue()));
    }

    @Test
    void listShouldSupportBusinessSearchByCode() throws Exception {
        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, null, null, false, "EQ-2026-0012", 0, 20))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        MvcResult result = mockMvc.perform(get("/api/v1/equipment").param("search", "EQ-2026-0012"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andReturn();

        assertNull(result.getResolvedException());

        verify(service).search(null, null, null, null, null, false, "EQ-2026-0012", 0, 20);
    }

    @Test
    void listShouldSupportBusinessSearchByNameAndReturnEmptyPage() throws Exception {
        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, null, null, false, "compressor", 0, 20))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        MvcResult result = mockMvc.perform(get("/api/v1/equipment").param("search", "compressor"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andReturn();

        assertNull(result.getResolvedException());

        verify(service).search(null, null, null, null, null, false, "compressor", 0, 20);
    }

    @Test
    void patchPlacementWarehouseSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, warehouseId,
                new EquipmentDto.Ref(warehouseId, "WH-001", "Main Warehouse"));
        when(service.updatePlacement(eq(id), any())).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "WAREHOUSE",
                                  "warehouseId": "%s"
                                }
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.departmentId").value(nullValue()))
                .andExpect(jsonPath("$.locationId").value(warehouseId.toString()));
    }

    @Test
    void patchPlacementDepartmentSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId, null, null);
        when(service.updatePlacement(eq(id), any())).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "DEPARTMENT",
                                  "departmentId": "%s"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.locationId").value(nullValue()));
    }

    @Test
    void patchPlacementInvalidMixedPayloadReturnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.updatePlacement(eq(id), any()))
                .thenThrow(RestException.badRequest("warehouseId and departmentId cannot both be provided"));

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "WAREHOUSE",
                                  "warehouseId": "%s",
                                  "departmentId": "%s"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("warehouseId and departmentId cannot both be provided"));
    }

    @Test
    void patchPlacementInvalidWarehouseReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(service.updatePlacement(eq(id), any()))
                .thenThrow(RestException.notFound("Warehouse not found: " + warehouseId));

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "WAREHOUSE",
                                  "warehouseId": "%s"
                                }
                                """.formatted(warehouseId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Warehouse not found: " + warehouseId));
    }

    @Test
    void patchPlacementInvalidDepartmentReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(service.updatePlacement(eq(id), any()))
                .thenThrow(RestException.notFound("Department not found: " + departmentId));

        mockMvc.perform(patch("/api/v1/equipment/{id}/placement", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetType": "DEPARTMENT",
                                  "departmentId": "%s"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department not found: " + departmentId));
    }

    private EquipmentDto equipmentDto(UUID id, UUID equipmentTypeId, UUID departmentId) {
        return equipmentDto(id, equipmentTypeId, departmentId, null, null);
    }

    private EquipmentDto equipmentDto(UUID id,
                                      UUID equipmentTypeId,
                                      UUID departmentId,
                                      UUID locationId,
                                      EquipmentDto.Ref location) {
        return new EquipmentDto(
                id,
                "EQ-2026-0020",
                "Compressor A",
                "INV-1",
                null,
                null,
                null,
                equipmentTypeId,
                departmentId,
                locationId,
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
                location,
                null,
                null,
                null
        );
    }

    private void assertNoResolvedException(MvcResult result) {
        Exception ex = result.getResolvedException();
        if (ex == null) {
            return;
        }
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String rootMessage = root != null ? root.getClass().getName() + ": " + root.getMessage() : "n/a";
        fail("Resolved exception: " + ex.getClass().getName() + ": " + ex.getMessage() + "; root cause: " + rootMessage);
    }
}
