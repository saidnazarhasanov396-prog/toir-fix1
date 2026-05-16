package com.toir.controller;

import com.toir.controller.equipment.EquipmentController;
import com.toir.dto.equipment.EquipmentDetailDto;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.PlacementType;
import com.toir.enums.WarehouseEquipmentStatus;
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
import com.toir.dto.equipment.EquipmentStatsResponse;

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
        EquipmentDto dto = equipmentDto(id, "EQ-2026-0020", equipmentTypeId, null);
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
    void listResponseIncludesPlacementObjectForDepartmentEquipment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        EquipmentDto.Ref departmentRef = new EquipmentDto.Ref(departmentId, "DEP-001", "Main Department");
        EquipmentDto.Ref locationRef = new EquipmentDto.Ref(locationId, "LOC-001", "Main Workshop");
        EquipmentDto.PlacementRef placement = new EquipmentDto.PlacementRef(
                PlacementType.DEPARTMENT,
                departmentRef,
                null,
                null,
                locationRef
        );
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, departmentId, locationId, locationRef, departmentRef, placement);

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, null, null, false, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].placement.type").value("DEPARTMENT"))
                .andExpect(jsonPath("$.content[0].placement.department.id").value(departmentId.toString()))
                .andExpect(jsonPath("$.content[0].placement.warehouse").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.warehouseStatus").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.location.id").value(locationId.toString()));
    }

    @Test
    void listResponseIncludesPlacementObjectForWarehouseEquipment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        EquipmentDto.Ref warehouseRef = new EquipmentDto.Ref(warehouseId, "WH-001", "Main Warehouse");
        EquipmentDto.PlacementRef placement = new EquipmentDto.PlacementRef(
                PlacementType.WAREHOUSE,
                null,
                warehouseRef,
                WarehouseEquipmentStatus.AVAILABLE,
                warehouseRef
        );
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, warehouseId, warehouseRef, null, placement);

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, null, null, false, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].placement.type").value("WAREHOUSE"))
                .andExpect(jsonPath("$.content[0].placement.department").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.warehouse.id").value(warehouseId.toString()))
                .andExpect(jsonPath("$.content[0].placement.warehouseStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[0].placement.location.id").value(warehouseId.toString()));
    }

    @Test
    void detailResponseIncludesRelatedArrays() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        EquipmentDto.Ref departmentRef = new EquipmentDto.Ref(departmentId, "DEP-010", "Assembly");
        EquipmentDto.PlacementRef placement = new EquipmentDto.PlacementRef(
                PlacementType.DEPARTMENT,
                departmentRef,
                null,
                null,
                null
        );
        EquipmentDto equipment = equipmentDto(id, equipmentTypeId, departmentId, null, null, departmentRef, placement);
        EquipmentDetailDto detail = new EquipmentDetailDto(
                equipment,
                List.of(new EquipmentDetailDto.RepairRequestShortDto(
                        UUID.randomUUID(),
                        "RR-001",
                        "Seal leak",
                        null,
                        null,
                        "Detected leak"
                )),
                List.of(new EquipmentDetailDto.DefectShortDto(
                        UUID.randomUUID(),
                        "DEF-001",
                        "Bearing overheating",
                        null,
                        null,
                        "Temperature high"
                )),
                List.of(new EquipmentDetailDto.WorkOrderShortDto(
                        UUID.randomUUID(),
                        "WO-001",
                        "Bearing replacement",
                        null,
                        null,
                        null,
                        "Replace bearing"
                )),
                List.of(new EquipmentDetailDto.DowntimeEventShortDto(
                        UUID.randomUUID(),
                        null,
                        null,
                        45,
                        null,
                        "Unexpected stop"
                ))
        );
        when(service.findDetailById(id)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/equipment/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment.placement.type").value("DEPARTMENT"))
                .andExpect(jsonPath("$.equipment.placement.department.id").value(departmentId.toString()))
                .andExpect(jsonPath("$.repairRequests").isArray())
                .andExpect(jsonPath("$.repairRequests.length()").value(1))
                .andExpect(jsonPath("$.defects").isArray())
                .andExpect(jsonPath("$.defects.length()").value(1))
                .andExpect(jsonPath("$.workOrders").isArray())
                .andExpect(jsonPath("$.workOrders.length()").value(1))
                .andExpect(jsonPath("$.downtimeEvents").isArray())
                .andExpect(jsonPath("$.downtimeEvents.length()").value(1));
    }

    @Test
    void detailResponseRelatedArraysAreEmptyNotNull() throws Exception {
        UUID id = UUID.randomUUID();
        EquipmentDto equipment = equipmentDto(id, UUID.randomUUID(), null);
        EquipmentDetailDto detail = new EquipmentDetailDto(equipment, List.of(), List.of(), List.of(), List.of());
        when(service.findDetailById(id)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/equipment/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequests").isArray())
                .andExpect(jsonPath("$.repairRequests.length()").value(0))
                .andExpect(jsonPath("$.defects").isArray())
                .andExpect(jsonPath("$.defects.length()").value(0))
                .andExpect(jsonPath("$.workOrders").isArray())
                .andExpect(jsonPath("$.workOrders.length()").value(0))
                .andExpect(jsonPath("$.downtimeEvents").isArray())
                .andExpect(jsonPath("$.downtimeEvents.length()").value(0));
    }

    @Test
    void detailUnknownEquipmentReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findDetailById(id))
                .thenThrow(RestException.notFound("Equipment not found: " + id));

        mockMvc.perform(get("/api/v1/equipment/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Equipment not found: " + id));
    }

    @Test
    void listResponsePlacementUnknownIsStable() throws Exception {
        UUID id = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        EquipmentDto.PlacementRef placement = new EquipmentDto.PlacementRef(
                PlacementType.UNKNOWN,
                null,
                null,
                null,
                null
        );
        EquipmentDto dto = equipmentDto(id, equipmentTypeId, null, null, null, null, placement);

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, null, null, false, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].placement.type").value("UNKNOWN"))
                .andExpect(jsonPath("$.content[0].placement.department").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.warehouse").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.warehouseStatus").value(nullValue()))
                .andExpect(jsonPath("$.content[0].placement.location").value(nullValue()));
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

    @Test
    void statsWithoutFiltersReturnsEquipmentStats() throws Exception {
        EquipmentStatsResponse response = new EquipmentStatsResponse(
                50,
                9,
                3,
                1
        );

        when(securityScope.enforceDepartmentScope(null)).thenReturn(null);
        when(service.getEquipmentStats(null, null, null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/equipment/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInRegistry").value(50))
                .andExpect(jsonPath("$.active").value(9))
                .andExpect(jsonPath("$.inRepair").value(3))
                .andExpect(jsonPath("$.decommissioned").value(1));

        verify(securityScope).enforceDepartmentScope(null);
        verify(service).getEquipmentStats(null, null, null, null);
    }

    @Test
    void statsWithFiltersPassesParamsToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID scopedDepartmentId = departmentId;
        UUID equipmentTypeId = UUID.randomUUID();

        EquipmentStatsResponse response = new EquipmentStatsResponse(
                12,
                8,
                2,
                2
        );

        when(securityScope.enforceDepartmentScope(departmentId)).thenReturn(scopedDepartmentId);
        when(service.getEquipmentStats(
                "pump",
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                scopedDepartmentId,
                equipmentTypeId
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/equipment/stats")
                        .param("search", "pump")
                        .param("category", "PRODUCTION_EQUIPMENT")
                        .param("departmentId", departmentId.toString())
                        .param("equipmentTypeId", equipmentTypeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInRegistry").value(12))
                .andExpect(jsonPath("$.active").value(8))
                .andExpect(jsonPath("$.inRepair").value(2))
                .andExpect(jsonPath("$.decommissioned").value(2));

        verify(securityScope).enforceDepartmentScope(departmentId);
        verify(service).getEquipmentStats(
                "pump",
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                scopedDepartmentId,
                equipmentTypeId
        );
    }

    private EquipmentDto equipmentDto(UUID id, UUID equipmentTypeId, UUID departmentId) {
        return equipmentDto(id, "EQ-2026-0020", equipmentTypeId, departmentId);
    }

    private EquipmentDto equipmentDto(UUID id, String code, UUID equipmentTypeId, UUID departmentId) {
        return equipmentDto(id, code, equipmentTypeId, departmentId, null, null, null, null);
    }

    private EquipmentDto equipmentDto(UUID id,
                                      UUID equipmentTypeId,
                                      UUID departmentId,
                                      UUID locationId,
                                      EquipmentDto.Ref location) {
        return equipmentDto(id, "EQ-2026-0020", equipmentTypeId, departmentId, locationId, location, null, null);
    }

    private EquipmentDto equipmentDto(UUID id,
                                      UUID equipmentTypeId,
                                      UUID departmentId,
                                      UUID locationId,
                                      EquipmentDto.Ref location,
                                      EquipmentDto.Ref departmentRef,
                                      EquipmentDto.PlacementRef placement) {
        return equipmentDto(id, "EQ-2026-0020", equipmentTypeId, departmentId, locationId, location, departmentRef, placement);
    }

    private EquipmentDto equipmentDto(UUID id,
                                      String code,
                                      UUID equipmentTypeId,
                                      UUID departmentId,
                                      UUID locationId,
                                      EquipmentDto.Ref location,
                                      EquipmentDto.Ref departmentRef,
                                      EquipmentDto.PlacementRef placement) {
        return new EquipmentDto(
                id,
                code,
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
                departmentRef,
                location,
                null,
                null,
                null,
                placement
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
