package com.toir.security;

import com.toir.controller.BrigadeController;
import com.toir.controller.CriticalityClassController;
import com.toir.controller.LocationController;
import com.toir.controller.department.DepartmentController;
import com.toir.controller.equipment.EquipmentTypeController;
import com.toir.dto.brigade.BrigadeDto;
import com.toir.dto.brigade.BrigadeRequest;
import com.toir.dto.criticalityclass.CriticalityClassDto;
import com.toir.dto.department.DepartmentDto;
import com.toir.dto.department.DepartmentRequest;
import com.toir.dto.equipmenttype.EquipmentTypeDto;
import com.toir.dto.equipmenttype.EquipmentTypeRequest;
import com.toir.dto.location.LocationDto;
import com.toir.dto.location.LocationRequest;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.DepartmentType;
import com.toir.enums.LocationType;
import com.toir.service.BrigadeService;
import com.toir.service.CriticalityClassService;
import com.toir.service.LocationService;
import com.toir.service.department.DepartmentService;
import com.toir.service.equipment.EquipmentTypeService;
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

@WebMvcTest(controllers = {
        DepartmentController.class,
        BrigadeController.class,
        LocationController.class,
        EquipmentTypeController.class,
        CriticalityClassController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacReferenceDataSecurityTest.SecurityBeans.class
})
class RbacReferenceDataSecurityTest {

    private static final String LOCATION_READ = "LOCATION_READ";
    private static final String LOCATION_CREATE = "LOCATION_CREATE";
    private static final String LOCATION_UPDATE = "LOCATION_UPDATE";
    private static final String LOCATION_DELETE = "LOCATION_DELETE";
    private static final String EQUIPMENT_TYPE_READ = "EQUIPMENT_TYPE_READ";
    private static final String EQUIPMENT_TYPE_CREATE = "EQUIPMENT_TYPE_CREATE";
    private static final String EQUIPMENT_TYPE_UPDATE = "EQUIPMENT_TYPE_UPDATE";
    private static final String EQUIPMENT_TYPE_DELETE = "EQUIPMENT_TYPE_DELETE";
    private static final String CATEGORY_READ = "CATEGORY_READ";
    private static final String CATEGORY_CREATE = "CATEGORY_CREATE";
    private static final String CATEGORY_UPDATE = "CATEGORY_UPDATE";
    private static final String CATEGORY_DELETE = "CATEGORY_DELETE";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    DepartmentService departmentService;

    @MockBean
    BrigadeService brigadeService;

    @MockBean
    LocationService locationService;

    @MockBean
    EquipmentTypeService equipmentTypeService;

    @MockBean
    CriticalityClassService criticalityClassService;

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
    void unauthenticatedCannotReadReferenceData() throws Exception {
        mockMvc.perform(get("/api/v1/departments?page=0&size=1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/brigades?page=0&size=1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/locations?page=0&size=1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/equipment-types?page=0&size=1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/criticality-classes?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadReferenceData() throws Exception {
        stubReadEndpoints();

        mockMvc.perform(get("/api/v1/departments?page=0&size=1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/brigades?page=0&size=1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/locations?page=0&size=1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/equipment-types?page=0&size=1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/criticality-classes?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {
            PermissionConstants.DEPARTMENT_READ,
            PermissionConstants.BRIGADE_READ,
            LOCATION_READ,
            EQUIPMENT_TYPE_READ,
            CATEGORY_READ
    })
    void exactReadPermissionsCanReadReferenceData() throws Exception {
        UUID id = UUID.randomUUID();
        stubReadEndpoints();
        when(departmentService.findById(id)).thenReturn(departmentDto(id));
        when(brigadeService.findById(id)).thenReturn(brigadeDto(id));
        when(locationService.findById(id)).thenReturn(locationDto(id));
        when(equipmentTypeService.findById(id)).thenReturn(equipmentTypeDto(id));
        when(criticalityClassService.findById(id)).thenReturn(criticalityClassDto(id));

        mockMvc.perform(get("/api/v1/departments?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/departments/{id}", id))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/departments/{id}/employees", id))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/brigades?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/brigades/{id}", id))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/brigades/{id}/members?page=0&size=1", id))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/locations?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/locations/{id}", id))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment-types?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment-types/{id}", id))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/criticality-classes?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/criticality-classes/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadReferenceData() throws Exception {
        stubReadEndpoints();

        mockMvc.perform(get("/api/v1/departments?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/brigades?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/locations?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment-types?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/criticality-classes?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadReferenceData() throws Exception {
        stubReadEndpoints();

        mockMvc.perform(get("/api/v1/departments?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/brigades?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/locations?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/equipment-types?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/criticality-classes?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEPARTMENT_CREATE)
    void departmentCreateCanCreateDepartment() throws Exception {
        when(departmentService.create(any(DepartmentRequest.class))).thenReturn(departmentDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(departmentPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEPARTMENT_UPDATE)
    void departmentUpdateCanUpdateDepartment() throws Exception {
        UUID id = UUID.randomUUID();
        when(departmentService.update(eq(id), any(DepartmentRequest.class))).thenReturn(departmentDto(id));

        mockMvc.perform(put("/api/v1/departments/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(departmentPayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEPARTMENT_DELETE)
    void departmentDeleteCanDeleteDepartment() throws Exception {
        mockMvc.perform(delete("/api/v1/departments/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BRIGADE_CREATE)
    void brigadeCreateCanCreateBrigadeAndMembers() throws Exception {
        UUID id = UUID.randomUUID();
        when(brigadeService.create(any(BrigadeRequest.class))).thenReturn(brigadeDto(id));
        when(brigadeService.addMember(eq(id), any())).thenReturn(null);

        mockMvc.perform(post("/api/v1/brigades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brigadePayload()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/brigades/{id}/members", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brigadeMemberPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BRIGADE_UPDATE)
    void brigadeUpdateCanUpdateBrigadeAndRemoveMembers() throws Exception {
        UUID id = UUID.randomUUID();
        when(brigadeService.update(eq(id), any(BrigadeRequest.class))).thenReturn(brigadeDto(id));

        mockMvc.perform(put("/api/v1/brigades/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brigadePayload()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/brigades/{id}/members/{memberId}", id, UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.BRIGADE_DELETE)
    void brigadeDeleteCanDeleteBrigade() throws Exception {
        mockMvc.perform(delete("/api/v1/brigades/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = LOCATION_CREATE)
    void locationCreateCanCreateLocation() throws Exception {
        when(locationService.create(any(LocationRequest.class))).thenReturn(locationDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = LOCATION_UPDATE)
    void locationUpdateCanUpdateLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(locationService.update(eq(id), any(LocationRequest.class))).thenReturn(locationDto(id));

        mockMvc.perform(put("/api/v1/locations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = LOCATION_DELETE)
    void locationDeleteCanDeleteLocation() throws Exception {
        mockMvc.perform(delete("/api/v1/locations/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = EQUIPMENT_TYPE_CREATE)
    void equipmentTypeCreateCanCreateEquipmentType() throws Exception {
        when(equipmentTypeService.create(any(EquipmentTypeRequest.class))).thenReturn(equipmentTypeDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/equipment-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(equipmentTypePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = EQUIPMENT_TYPE_UPDATE)
    void equipmentTypeUpdateCanUpdateEquipmentType() throws Exception {
        UUID id = UUID.randomUUID();
        when(equipmentTypeService.update(eq(id), any(EquipmentTypeRequest.class))).thenReturn(equipmentTypeDto(id));

        mockMvc.perform(put("/api/v1/equipment-types/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(equipmentTypePayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = EQUIPMENT_TYPE_DELETE)
    void equipmentTypeDeleteCanDeleteEquipmentType() throws Exception {
        mockMvc.perform(delete("/api/v1/equipment-types/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = CATEGORY_CREATE)
    void categoryCreateCanCreateDictionaryItem() throws Exception {
        when(criticalityClassService.create(any(CriticalityClassDto.class))).thenReturn(criticalityClassDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/criticality-classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criticalityPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = CATEGORY_UPDATE)
    void categoryUpdateCanUpdateDictionaryItem() throws Exception {
        UUID id = UUID.randomUUID();
        when(criticalityClassService.update(eq(id), any(CriticalityClassDto.class))).thenReturn(criticalityClassDto(id));

        mockMvc.perform(put("/api/v1/criticality-classes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criticalityPayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = CATEGORY_DELETE)
    void categoryDeleteCanDeleteDictionaryItem() throws Exception {
        mockMvc.perform(delete("/api/v1/criticality-classes/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = {
            PermissionConstants.DEPARTMENT_READ,
            PermissionConstants.BRIGADE_READ,
            LOCATION_READ,
            EQUIPMENT_TYPE_READ,
            CATEGORY_READ
    })
    void readOnlyCannotMutateReferenceData() throws Exception {
        assertMutationsForbidden();
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotMutateReferenceData() throws Exception {
        assertMutationsForbidden();
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotMutateReferenceData() throws Exception {
        assertMutationsForbidden();
    }

    private void assertMutationsForbidden() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(departmentPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/departments/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(departmentPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/departments/{id}", id))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/brigades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brigadePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/brigades/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brigadePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/brigades/{id}", id))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/locations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/locations/{id}", id))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/equipment-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(equipmentTypePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/equipment-types/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(equipmentTypePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/equipment-types/{id}", id))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/criticality-classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criticalityPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/criticality-classes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criticalityPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/criticality-classes/{id}", id))
                .andExpect(status().isForbidden());
    }

    private void stubReadEndpoints() {
        UUID id = UUID.randomUUID();
        when(departmentService.findAll(isNull(), eq(""))).thenReturn(List.of(departmentDto(id)));
        when(departmentService.findEmployeesByDepartment(any())).thenReturn(List.of());
        when(brigadeService.findAll(isNull(), isNull(), isNull())).thenReturn(List.of(brigadeDto(id)));
        when(brigadeService.listMembers(any())).thenReturn(List.of());
        when(locationService.search(isNull(), isNull(), eq(0), eq(1)))
                .thenReturn(new PageImpl<>(List.of(locationDto(id)), PageRequest.of(0, 1), 1));
        when(equipmentTypeService.findAll(isNull(), isNull())).thenReturn(List.of(equipmentTypeDto(id)));
        when(criticalityClassService.findAll(isNull())).thenReturn(List.of(criticalityClassDto(id)));
    }

    private DepartmentDto departmentDto(UUID id) {
        return new DepartmentDto(id, "DEP-001", "Mechanical", null, null, DepartmentType.WORKSHOP, null, null);
    }

    private BrigadeDto brigadeDto(UUID id) {
        return new BrigadeDto(id, "BR-001", "Mechanics", UUID.randomUUID(), null, "Mechanical", true, List.of());
    }

    private LocationDto locationDto(UUID id) {
        return new LocationDto(id, "LOC-001", "Workshop", null, null, LocationType.WORKSHOP, null, null, null);
    }

    private EquipmentTypeDto equipmentTypeDto(UUID id) {
        return new EquipmentTypeDto(id, "ET-001", "Pump", null, null, "PUMP", null);
    }

    private CriticalityClassDto criticalityClassDto(UUID id) {
        return new CriticalityClassDto(id, "CRIT-001", "Low", null, null, CriticalityLevel.LOW, null, 0, 0, 0, 0, null, 0);
    }

    private String departmentPayload() {
        return """
                {
                  "code": "DEP-001",
                  "name": "Mechanical",
                  "type": "WORKSHOP"
                }
                """;
    }

    private String brigadePayload() {
        return """
                {
                  "code": "BR-001",
                  "name": "Mechanics"
                }
                """;
    }

    private String brigadeMemberPayload() {
        return """
                {
                  "userId": "%s",
                  "roleCode": "MECHANIC"
                }
                """.formatted(UUID.randomUUID());
    }

    private String locationPayload() {
        return """
                {
                  "code": "LOC-001",
                  "name": "Workshop",
                  "type": "WORKSHOP"
                }
                """;
    }

    private String equipmentTypePayload() {
        return """
                {
                  "name": "Pump",
                  "nameUz": "Nasos",
                  "nameEn": "Pump",
                  "category": "PUMP"
                }
                """;
    }

    private String criticalityPayload() {
        return """
                {
                  "name": "Low",
                  "level": "LOW"
                }
                """;
    }
}
