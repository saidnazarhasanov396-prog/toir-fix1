package com.toir.controller;

import com.toir.controller.repair.RepairRequestController;
import com.toir.dto.meter.MeterReadingDto;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestMeterRequirementDto;
import com.toir.dto.repairrequest.RepairRequestStatsResponse;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.dto.triad.WorkOrderBriefDto;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.DefectStatus;
import com.toir.enums.MeterReadingContext;
import com.toir.enums.MeterSource;
import com.toir.enums.MeterType;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.ApprovalService;
import com.toir.service.repair.RepairRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RepairRequestControllerContractTest {

    @Mock
    RepairRequestService service;

    @Mock
    ApprovalService approvalService;

    @Mock
    RepairRequestRepository repository;

    @Mock
    ScopeAccessService scopeAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        mockMvc = MockMvcBuilders.standaloneSetup(new RepairRequestController(service, approvalService, repository, scopeAccessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithEquipmentIdAndApprovedStatusReturnsOnlyMatchingRepairRequests() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(UUID.randomUUID());
        when(service.search(RequestStatus.APPROVED, null, equipmentId, null, 0, 100, null))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/v1/repair-requests")
                        .param("size", "100")
                        .param("status", "APPROVED")
                        .param("equipmentId", equipmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(response.id().toString()));

        verify(scopeAccessService).enforceDepartmentScope(null);
        verify(service).search(RequestStatus.APPROVED, null, equipmentId, null, 0, 100, null);
    }

    @Test
    void listWithEquipmentIdReturnsOnlyMatchingRepairRequests() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(UUID.randomUUID());
        when(service.search(null, null, equipmentId, null, 0, 100, null))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/v1/repair-requests")
                        .param("size", "100")
                        .param("equipmentId", equipmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(response.id().toString()));

        verify(scopeAccessService).enforceDepartmentScope(null);
        verify(service).search(null, null, equipmentId, null, 0, 100, null);
    }

    @Test
    void listWithStatusReturnsOnlyMatchingRepairRequests() throws Exception {
        RepairRequestDto response = dtoWithLinks(UUID.randomUUID());
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(RequestStatus.APPROVED, null, null, null, 0, 100, null))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/v1/repair-requests")
                        .param("size", "100")
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(response.id().toString()));

        verify(scopeAccessService).enforceDepartmentScope(null);
        verify(service).search(RequestStatus.APPROVED, null, null, null, 0, 100, null);
    }

    @Test
    void listWithEquipmentIdAndStatusNoMatchesReturnsEmptyPage() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(service.search(RequestStatus.APPROVED, null, equipmentId, null, 0, 100, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(get("/api/v1/repair-requests")
                        .param("size", "100")
                        .param("status", "APPROVED")
                        .param("equipmentId", equipmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(scopeAccessService).enforceDepartmentScope(null);
        verify(service).search(RequestStatus.APPROVED, null, equipmentId, null, 0, 100, null);
    }

    @Test
    void listWithoutEquipmentIdKeepsExistingBehavior() throws Exception {
        UUID scopedDepartmentId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(UUID.randomUUID());
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(scopedDepartmentId);
        when(service.search(null, scopedDepartmentId, null, null, 0, 100, null))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/v1/repair-requests")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(response.id().toString()));

        verify(scopeAccessService).enforceDepartmentScope(null);
        verify(service).search(null, scopedDepartmentId, null, null, 0, 100, null);
    }

    @Test
    void detailIncludesLinkedDefects() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(requestId);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(entityFromDto(response)));
        when(service.findById(requestId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedDefects[0].id").value(response.linkedDefects().getFirst().id().toString()))
                .andExpect(jsonPath("$.linkedDefects[0].code").value("DEF-2026-1001"));
    }

    @Test
    void detailIncludesLinkedWorkOrders() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(requestId);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(entityFromDto(response)));
        when(service.findById(requestId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedWorkOrders[0].id").value(response.linkedWorkOrders().getFirst().id().toString()))
                .andExpect(jsonPath("$.linkedWorkOrders[0].number").value("WO-2026-1001"));
    }

    @Test
    void detailWithNoLinksReturnsEmptyArrays() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithoutLinks(requestId);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(entityFromDto(response)));
        when(service.findById(requestId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedDefects").isArray())
                .andExpect(jsonPath("$.linkedDefects").isEmpty())
                .andExpect(jsonPath("$.linkedWorkOrders").isArray())
                .andExpect(jsonPath("$.linkedWorkOrders").isEmpty());
    }

    @Test
    void detailIncludesEquipmentDepartmentReporterIds() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(requestId);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(entityFromDto(response)));
        when(service.findById(requestId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(response.equipmentId().toString()))
                .andExpect(jsonPath("$.departmentId").value(response.departmentId().toString()))
                .andExpect(jsonPath("$.reporterId").value(response.reporterId().toString()));
    }

    @Test
    void assignedUserCanReadRequestOutsideDepartmentScope() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        RepairRequestDto response = dtoWithAssignee(requestId, assigneeId);
        RepairRequest entity = entityFromDto(response);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(response.departmentId())).thenReturn(false);
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(assigneeId);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(entity));
        when(service.findById(requestId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").value(assigneeId.toString()));
    }

    @Test
    void listIncludesEquipmentDepartmentReporterIds() throws Exception {
        RepairRequestDto response = dtoWithLinks(UUID.randomUUID());
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, null, 0, 20, null))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/repair-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentId").value(response.equipmentId().toString()))
                .andExpect(jsonPath("$.content[0].departmentId").value(response.departmentId().toString()))
                .andExpect(jsonPath("$.content[0].reporterId").value(response.reporterId().toString()));
    }

    @Test
    void detailWithNullableIdsReturnsNullsNot500() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithNullableIds(requestId);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(entityFromDto(response)));
        when(service.findById(requestId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(nullValue()))
                .andExpect(jsonPath("$.departmentId").value(nullValue()))
                .andExpect(jsonPath("$.reporterId").value(nullValue()));
    }

    @Test
    void statsWithoutFiltersReturnsRepairRequestStats() throws Exception {
        RepairRequestStatsResponse response = new RepairRequestStatsResponse(
                24,
                3,
                8,
                12
        );

        when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        when(service.getStats(null, null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(24))
                .andExpect(jsonPath("$.emergency").value(3))
                .andExpect(jsonPath("$.open").value(8))
                .andExpect(jsonPath("$.withWorkOrder").value(12));

        verify(scopeAccessService).enforceDepartmentScope(null);
        verify(service).getStats(null, null, null);
    }

    @Test
    void statsWithFiltersPassesScopedDepartmentEquipmentAndSearchToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID scopedDepartmentId = departmentId;
        UUID equipmentId = UUID.randomUUID();

        RepairRequestStatsResponse response = new RepairRequestStatsResponse(
                10,
                2,
                4,
                5
        );

        when(scopeAccessService.enforceDepartmentScope(departmentId)).thenReturn(scopedDepartmentId);
        when(service.getStats(scopedDepartmentId, equipmentId, "pump")).thenReturn(response);

        mockMvc.perform(get("/api/v1/repair-requests/stats")
                        .param("departmentId", departmentId.toString())
                        .param("equipmentId", equipmentId.toString())
                        .param("search", "pump"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(10))
                .andExpect(jsonPath("$.emergency").value(2))
                .andExpect(jsonPath("$.open").value(4))
                .andExpect(jsonPath("$.withWorkOrder").value(5));

        verify(scopeAccessService).enforceDepartmentScope(departmentId);
        verify(service).getStats(scopedDepartmentId, equipmentId, "pump");
    }

    @Test
    void approveEndpointCreatesApprovalRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(requestId);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(entityFromDto(response)));

        mockMvc.perform(post("/api/v1/repair-requests/{id}/approve", requestId))
                .andExpect(status().isOk());

        verify(approvalService).createOrReuseApprovalForDocument(
                org.mockito.ArgumentMatchers.eq("REPAIR_REQUEST"),
                org.mockito.ArgumentMatchers.eq(requestId),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("REPAIR_REQUEST_APPROVER"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(service, never()).assertMeterReadingsReadyForApproval(requestId);
        verify(service, never()).approve(requestId);
    }

    @Test
    void meterRequirementsEndpointReturnsConfiguredRepairRequestMeters() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(requestId);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(entityFromDto(response)));
        when(service.getMeterRequirements(requestId)).thenReturn(List.of(new RepairRequestMeterRequirementDto(
                meterId,
                MeterType.MILEAGE_KM,
                "Odometer",
                "km",
                9_000.0,
                false,
                true,
                10_000.0,
                Instant.parse("2026-06-15T06:30:00Z")
        )));

        mockMvc.perform(get("/api/v1/repair-requests/{id}/meter-readings/requirements", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].meterId").value(meterId.toString()))
                .andExpect(jsonPath("$[0].meterType").value("MILEAGE_KM"))
                .andExpect(jsonPath("$[0].required").value(false))
                .andExpect(jsonPath("$[0].provided").value(true))
                .andExpect(jsonPath("$[0].latestValue").value(10_000.0));
    }

    @Test
    void addMeterReadingsEndpointPassesBatchToService() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID readingId = UUID.randomUUID();
        UUID recordedByUserId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(requestId);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(entityFromDto(response)));
        when(service.addMeterReadings(eq(requestId), any())).thenReturn(List.of(new MeterReadingDto(
                readingId,
                meterId,
                equipmentId,
                10_000.0,
                1_000.0,
                Instant.parse("2026-06-15T06:30:00Z"),
                MeterSource.MANUAL,
                recordedByUserId,
                "Mechanic",
                "Odometer",
                "Truck",
                "tablet-1",
                "breakdown intake",
                Instant.parse("2026-06-15T06:31:00Z"),
                requestId,
                null,
                null,
                MeterReadingContext.FAILURE_DETECTED
        )));

        mockMvc.perform(post("/api/v1/repair-requests/{id}/meter-readings", requestId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "readings": [
                                    {
                                      "meterId": "%s",
                                      "value": 10000.0,
                                      "readAt": "2026-06-15T06:30:00Z",
                                      "recordedByUserId": "%s",
                                      "deviceId": "tablet-1",
                                      "note": "breakdown intake"
                                    }
                                  ]
                                }
                                """.formatted(meterId, recordedByUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(readingId.toString()))
                .andExpect(jsonPath("$[0].repairRequestId").value(requestId.toString()))
                .andExpect(jsonPath("$[0].readingContext").value("FAILURE_DETECTED"));

        ArgumentCaptor<com.toir.dto.repairrequest.RepairRequestMeterReadingBatchRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.repairrequest.RepairRequestMeterReadingBatchRequest.class);
        verify(service).addMeterReadings(eq(requestId), captor.capture());
        assertThat(captor.getValue().readings()).hasSize(1);
        assertThat(captor.getValue().readings().getFirst().meterId()).isEqualTo(meterId);
        assertThat(captor.getValue().readings().getFirst().value()).isEqualTo(10_000.0);
    }

    @Test
    void statusEndpointPassesOverrideReasonToService() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinks(requestId);
        when(repository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(entityFromDto(response)));
        when(service.changeStatus(requestId, RequestStatus.CANCELLED, "duplicate cleanup")).thenReturn(response);

        mockMvc.perform(post("/api/v1/repair-requests/{id}/status", requestId)
                        .param("status", "CANCELLED")
                        .param("reason", "duplicate cleanup"))
                .andExpect(status().isOk());

        verify(service).changeStatus(requestId, RequestStatus.CANCELLED, "duplicate cleanup");
    }

    @Test
    void createEndpointAcceptsMissingDefectId() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithoutLinks(requestId);
        when(service.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType("application/json")
                        .content("""
                                {
                                  "number": "RR-2026-0001",
                                  "title": "Pump vibration",
                                  "description": "Excess vibration on pump",
                                  "equipmentId": "%s",
                                  "departmentId": "%s",
                                  "reporterId": "%s"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.linkedDefects").isArray())
                .andExpect(jsonPath("$.linkedDefects").isEmpty());

        verify(service).create(any());
    }

    @Test
    void createEndpointReturnsNotFoundForUnknownDefect() throws Exception {
        UUID defectId = UUID.randomUUID();
        when(service.create(any())).thenThrow(com.toir.exception.RestException.notFound("Defect not found: " + defectId));

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType("application/json")
                        .content(createPayload(defectId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createEndpointReturnsBadRequestForDefectEquipmentMismatch() throws Exception {
        when(service.create(any())).thenThrow(com.toir.exception.RestException.badRequest("Defect belongs to a different equipment"));

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType("application/json")
                        .content(createPayload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEndpointAcceptsDefectIdAndReturnsLinkedDefect() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinkedDefect(requestId, defectId);
        when(service.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType("application/json")
                        .content(createPayload(defectId, response.equipmentId(), response.departmentId(), response.reporterId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.linkedDefects[0].id").value(defectId.toString()));
    }

    @Test
    void createEndpointAcceptsMissingDepartmentIdWhenEquipmentIsProvided() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        RepairRequestDto response = dtoWithoutLinks(requestId);
        when(service.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType("application/json")
                        .content("""
                                {
                                  "number": "RR-2026-0001",
                                  "title": "Pump vibration",
                                  "description": "Excess vibration on pump",
                                  "equipmentId": "%s",
                                  "reporterId": "%s",
                                  "priority": "HIGH",
                                  "criticality": "HIGH",
                                  "source": "MANUAL"
                                }
                                """.formatted(equipmentId, reporterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(requestId.toString()));

        ArgumentCaptor<com.toir.dto.repairrequest.RepairRequestRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.repairrequest.RepairRequestRequest.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().equipmentId()).isEqualTo(equipmentId);
        assertThat(captor.getValue().departmentId()).isNull();
    }

    @Test
    void createEndpointAcceptsInlineDefectsArrayAndReturnsLinkedDefect() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        RepairRequestDto response = dtoWithLinkedDefect(requestId, defectId);
        when(service.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType("application/json")
                        .content("""
                                {
                                  "number": "RR-2026-0001",
                                  "title": "Pump vibration",
                                  "description": "Excess vibration on pump",
                                  "equipmentId": "%s",
                                  "departmentId": "%s",
                                  "reporterId": "%s",
                                  "priority": "HIGH",
                                  "criticality": "HIGH",
                                  "source": "MANUAL",
                                  "defects": [
                                    {
                                      "title": "Bearing wear",
                                      "description": "Noise from bearing",
                                      "category": "Mechanical",
                                      "severity": "HIGH"
                                    }
                                  ]
                                }
                                """.formatted(equipmentId, departmentId, reporterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.linkedDefects[0].id").value(defectId.toString()));

        ArgumentCaptor<com.toir.dto.repairrequest.RepairRequestRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.repairrequest.RepairRequestRequest.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().defects()).hasSize(1);
        assertThat(captor.getValue().defects().getFirst().title()).isEqualTo("Bearing wear");
        assertThat(captor.getValue().defectId()).isNull();
    }

    @Test
    void createEndpointAcceptsEmptyInlineDefectsArray() throws Exception {
        UUID requestId = UUID.randomUUID();
        RepairRequestDto response = dtoWithoutLinks(requestId);
        when(service.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType("application/json")
                        .content("""
                                {
                                  "number": "RR-2026-0001",
                                  "title": "Pump vibration",
                                  "description": "Excess vibration on pump",
                                  "equipmentId": "%s",
                                  "departmentId": "%s",
                                  "reporterId": "%s",
                                  "defects": []
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkedDefects").isEmpty());
    }

    @Test
    void createEndpointReturnsBadRequestForDefectIdAndInlineDefectConflict() throws Exception {
        when(service.create(any())).thenThrow(
                com.toir.exception.RestException.badRequest("Use either defectId or inline defects, not both"));

        mockMvc.perform(post("/api/v1/repair-requests")
                        .contentType("application/json")
                        .content("""
                                {
                                  "number": "RR-2026-0001",
                                  "title": "Pump vibration",
                                  "description": "Excess vibration on pump",
                                  "defectId": "%s",
                                  "equipmentId": "%s",
                                  "departmentId": "%s",
                                  "reporterId": "%s",
                                  "defects": [
                                    {
                                      "title": "Bearing wear",
                                      "description": "Noise from bearing"
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Use either defectId or inline defects, not both"));
    }

    private RepairRequest entityFromDto(RepairRequestDto dto) {
        RepairRequest entity = new RepairRequest();
        entity.setId(dto.id());
        entity.setNumber(dto.number());
        entity.setTitle(dto.title());
        entity.setDescription(dto.description());
        entity.setEquipmentId(dto.equipmentId());
        entity.setDepartmentId(dto.departmentId());
        entity.setReporterId(dto.reporterId());
        entity.setAssignedToId(dto.assignedToId());
        entity.setPriority(dto.priority());
        entity.setCriticality(dto.criticality());
        entity.setStatus(dto.status());
        entity.setSource(dto.source());
        return entity;
    }

    private RepairRequestDto dtoWithLinks(UUID requestId) {
        return new RepairRequestDto(
                requestId,
                "RR-2026-1001",
                "Repair request",
                "Description",
                UUID.randomUUID(),
                "Pump #1",
                UUID.randomUUID(),
                "Maintenance",
                null,
                UUID.randomUUID(),
                "Reporter",
                null,
                PriorityLevel.MEDIUM,
                CriticalityLevel.MEDIUM,
                RequestStatus.OPEN,
                RequestSource.MANUAL,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                null,
                null,
                null,
                List.of(new DefectBriefDto(
                        UUID.randomUUID(),
                        "DEF-2026-1001",
                        "Leak",
                        DefectStatus.OPEN,
                        "HIGH",
                        Instant.now()
                )),
                List.of(new WorkOrderBriefDto(
                        UUID.randomUUID(),
                        "WO-2026-1001",
                        WorkOrderStatus.APPROVED,
                        WorkType.REPAIR,
                        PriorityLevel.MEDIUM,
                        Instant.now(),
                        Instant.now().plusSeconds(3600)
                ))
        );
    }

    private RepairRequestDto dtoWithLinkedDefect(UUID requestId, UUID defectId) {
        return new RepairRequestDto(
                requestId,
                "RR-2026-1001",
                "Repair request",
                "Description",
                UUID.randomUUID(),
                "Pump #1",
                UUID.randomUUID(),
                "Maintenance",
                null,
                UUID.randomUUID(),
                "Reporter",
                null,
                PriorityLevel.MEDIUM,
                CriticalityLevel.MEDIUM,
                RequestStatus.OPEN,
                RequestSource.MANUAL,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                null,
                null,
                null,
                List.of(new DefectBriefDto(
                        defectId,
                        "DEF-2026-1001",
                        "Leak",
                        DefectStatus.OPEN,
                        "HIGH",
                        Instant.now()
                )),
                List.of()
        );
    }

    private RepairRequestDto dtoWithAssignee(UUID requestId, UUID assigneeId) {
        return new RepairRequestDto(
                requestId,
                "RR-2026-1001",
                "Repair request",
                "Description",
                UUID.randomUUID(),
                "Pump #1",
                UUID.randomUUID(),
                "Maintenance",
                null,
                UUID.randomUUID(),
                "Reporter",
                assigneeId,
                PriorityLevel.MEDIUM,
                CriticalityLevel.MEDIUM,
                RequestStatus.ASSIGNED,
                RequestSource.MANUAL,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private String createPayload(UUID defectId, UUID equipmentId, UUID departmentId, UUID reporterId) {
        return """
                {
                  "number": "RR-2026-0001",
                  "title": "Pump vibration",
                  "description": "Excess vibration on pump",
                  "defectId": "%s",
                  "equipmentId": "%s",
                  "departmentId": "%s",
                  "reporterId": "%s",
                  "priority": "HIGH",
                  "criticality": "HIGH",
                  "source": "MANUAL"
                }
                """.formatted(defectId, equipmentId, departmentId, reporterId);
    }

    private RepairRequestDto dtoWithoutLinks(UUID requestId) {
        return new RepairRequestDto(
                requestId,
                "RR-2026-1002",
                "Repair request",
                "Description",
                UUID.randomUUID(),
                "Pump #2",
                UUID.randomUUID(),
                "Maintenance",
                null,
                UUID.randomUUID(),
                "Reporter",
                null,
                PriorityLevel.MEDIUM,
                CriticalityLevel.MEDIUM,
                RequestStatus.OPEN,
                RequestSource.MANUAL,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private RepairRequestDto dtoWithNullableIds(UUID requestId) {
        return new RepairRequestDto(
                requestId,
                "RR-2026-1003",
                "Repair request",
                "Description",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                PriorityLevel.MEDIUM,
                CriticalityLevel.MEDIUM,
                RequestStatus.OPEN,
                RequestSource.MANUAL,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
