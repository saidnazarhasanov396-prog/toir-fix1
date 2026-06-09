package com.toir.controller;

import com.toir.dto.workorder.WorkOrderCalendarBucketDto;
import com.toir.dto.workorder.WorkOrderCalendarSummaryResponse;
import com.toir.dto.workorder.WorkOrderDocumentDto;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderPerformerOptionDto;
import com.toir.dto.workorder.WorkOrderStatusCountDto;
import com.toir.dto.triad.DefectBriefDto;
import com.toir.dto.triad.RepairRequestBriefDto;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.enums.DefectStatus;
import com.toir.enums.EquipmentNodeType;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.WorkOrderService;
import org.springframework.core.MethodParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class WorkOrderControllerContractTest {

    @Mock
    WorkOrderService service;

    @Mock
    WorkOrderRepository repository;

    @Mock
    ScopeAccessService scopeAccessService;

    private MockMvc mockMvc;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        lenient().when(repository.findByIdAndIsDeletedFalse(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(workOrderEntity(invocation.getArgument(0), UUID.randomUUID())));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(currentUserId.toString(), "user", "user@example.com", "User", null, "USER", List.of()),
                null
        ));
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkOrderController(service, repository, scopeAccessService))
                .setCustomArgumentResolvers(new TestCurrentUserResolver(currentUserId))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createWithUnknownRepairRequestReturns404() throws Exception {
        when(service.create(any(), eq(currentUserId))).thenThrow(RestException.notFound("Repair request not found: " + UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(baseCreateRequestJson(UUID.randomUUID(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Repair request not found")));
    }

    @Test
    void createWithUnknownDefectReturns404() throws Exception {
        when(service.create(any(), eq(currentUserId))).thenThrow(RestException.notFound("Defect not found: " + UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(baseCreateRequestJson(null, UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Defect not found")));
    }

    @Test
    void createWithMismatchedRepairRequestAndDefectReturns400() throws Exception {
        when(service.create(any(), eq(currentUserId))).thenThrow(RestException.badRequest("Defect belongs to a different repair request"));

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(baseCreateRequestJson(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("different repair request")));
    }

    @Test
    void createEmergencyWorkOrderWithoutRepairRequestReturns400() throws Exception {
        when(service.create(any(), eq(currentUserId))).thenThrow(RestException.badRequest(
                "repairRequestId is required when work order type is EMERGENCY"));

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(baseCreateRequestJson(WorkOrderType.EMERGENCY, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/work-orders"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "repairRequestId is required")));
    }

    @Test
    void createDefectWorkOrderWithoutDefectReturns400() throws Exception {
        when(service.create(any(), eq(currentUserId))).thenThrow(RestException.badRequest(
                "defectId is required when work order type is DEFECT"));

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(baseCreateRequestJson(WorkOrderType.DEFECT, UUID.randomUUID(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/work-orders"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "defectId is required")));
    }

    @Test
    void createWorkOrder_acceptsEquipmentNodeId() throws Exception {
        UUID equipmentNodeId = UUID.randomUUID();
        WorkOrderDto response = workOrderDtoWithNode(UUID.randomUUID(), equipmentNodeId);
        when(service.create(any(), eq(currentUserId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(baseCreateRequestJson(UUID.randomUUID(), null, equipmentNodeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipmentNodeId").value(equipmentNodeId.toString()));

        org.mockito.ArgumentCaptor<com.toir.dto.workorder.WorkOrderRequest> captor = forClass(com.toir.dto.workorder.WorkOrderRequest.class);
        verify(service).create(captor.capture(), eq(currentUserId));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().equipmentNodeId()).isEqualTo(equipmentNodeId);
    }

    @Test
    void createWorkOrder_acceptsPerformerId() throws Exception {
        UUID performerId = UUID.randomUUID();
        WorkOrderDto response = workOrderDtoWithPerformer(UUID.randomUUID(), performerId, "Ivan Petrov");
        when(service.create(any(), eq(currentUserId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(baseCreateRequestJsonWithPerformer(performerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.performerId").value(performerId.toString()))
                .andExpect(jsonPath("$.performerName").value("Ivan Petrov"));

        org.mockito.ArgumentCaptor<com.toir.dto.workorder.WorkOrderRequest> captor = forClass(com.toir.dto.workorder.WorkOrderRequest.class);
        verify(service).create(captor.capture(), eq(currentUserId));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().performerId()).isEqualTo(performerId);
    }

    @Test
    void responseIncludesRepairRequestObject() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(workOrderId, repairRequestBrief(), defectBrief());
        when(service.findById(workOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequest.id").value(response.repairRequest().id().toString()))
                .andExpect(jsonPath("$.repairRequest.number").value(response.repairRequest().number()))
                .andExpect(jsonPath("$.repairRequest.status").value(response.repairRequest().status().name()));
    }

    @Test
    void responseIncludesDefectObject() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(workOrderId, repairRequestBrief(), defectBrief());
        when(service.findById(workOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defect.id").value(response.defect().id().toString()))
                .andExpect(jsonPath("$.defect.code").value(response.defect().code()))
                .andExpect(jsonPath("$.defect.status").value(response.defect().status().name()));
    }

    @Test
    void attachDocumentsUploadsMultipleMultipartFilesWithDocumentNames() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDocumentDto first = workOrderDocumentDto(workOrderId, "Defect photo");
        WorkOrderDocumentDto second = workOrderDocumentDto(workOrderId, "Completion act");
        when(service.attachDocuments(eq(workOrderId), any(), any(), eq("ACT"), any()))
                .thenReturn(List.of(first, second));

        mockMvc.perform(multipart("/api/v1/work-orders/{id}/documents", workOrderId)
                        .file(new MockMultipartFile("files", "photo.png", "image/png", "png".getBytes()))
                        .file(new MockMultipartFile("files", "act.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .param("documentNames", "Defect photo", "Completion act")
                        .param("documentType", "ACT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].workOrderId").value(workOrderId.toString()))
                .andExpect(jsonPath("$[0].documentName").value("Defect photo"))
                .andExpect(jsonPath("$[0].uploadedById").value(currentUserId.toString()))
                .andExpect(jsonPath("$[1].documentName").value("Completion act"));

        var userCaptor = forClass(AuthenticatedUser.class);
        verify(service).attachDocuments(eq(workOrderId), any(), any(), eq("ACT"), userCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(userCaptor.getValue().id()).isEqualTo(currentUserId.toString());
    }

    @Test
    void attachDocumentsWithMismatchedDocumentNamesReturnsBadRequest() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(service.attachDocuments(eq(workOrderId), any(), any(), isNull(), any()))
                .thenThrow(RestException.badRequest("files and documentNames must have the same length"));

        mockMvc.perform(multipart("/api/v1/work-orders/{id}/documents", workOrderId)
                        .file(new MockMultipartFile("files", "act.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .param("documentNames", "One", "Two"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("files and documentNames must have the same length"));
    }

    @Test
    void listDocumentsReturnsPagedWorkOrderDocumentDtos() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(service.getDocuments(eq(workOrderId), any()))
                .thenReturn(List.of(workOrderDocumentDto(workOrderId, "Checklist")));

        mockMvc.perform(get("/api/v1/work-orders/{id}/documents?page=0&size=20", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].workOrderId").value(workOrderId.toString()))
                .andExpect(jsonPath("$.content[0].documentName").value("Checklist"))
                .andExpect(jsonPath("$.content[0].downloadUrl").value(org.hamcrest.Matchers.containsString(
                        "/api/v1/work-orders/" + workOrderId + "/documents/")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void downloadDocumentReturnsBlobWithOriginalFilename() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        WorkOrderDocumentDto dto = workOrderDocumentDto(workOrderId, documentId, "Completion act", "act.pdf", "application/pdf");
        when(service.getDocument(eq(workOrderId), eq(documentId), any())).thenReturn(dto);
        when(service.downloadDocument(eq(workOrderId), eq(documentId), any()))
                .thenReturn(new ByteArrayResource("%PDF-1.4\n".getBytes()));

        mockMvc.perform(get("/api/v1/work-orders/{id}/documents/{documentId}/download", workOrderId, documentId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("act.pdf")));
    }

    @Test
    void deleteDocumentReturnsNoContent() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/work-orders/{id}/documents/{documentId}", workOrderId, documentId))
                .andExpect(status().isNoContent());

        verify(service).deleteDocument(eq(workOrderId), eq(documentId), any());
    }

    @Test
    void documentEndpointsRejectWorkOrderOutsideDepartmentScope() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrderEntity(workOrderId, departmentId)));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        mockMvc.perform(get("/api/v1/work-orders/{id}/documents", workOrderId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWorkOrder_returnsEquipmentNodeReference() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID equipmentNodeId = UUID.randomUUID();
        WorkOrderDto response = workOrderDtoWithNode(workOrderId, equipmentNodeId);
        when(service.findById(workOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentNodeId").value(equipmentNodeId.toString()))
                .andExpect(jsonPath("$.equipmentNodeCode").value("BRG-01"))
                .andExpect(jsonPath("$.equipmentNodeName").value("Bearing"))
                .andExpect(jsonPath("$.equipmentNodeType").value("COMPONENT"));
    }

    @Test
    void getWorkOrder_returnsPerformerDetails() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        WorkOrderDto response = workOrderDtoWithPerformer(workOrderId, performerId, "Ivan Petrov");
        when(service.findById(workOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performerId").value(performerId.toString()))
                .andExpect(jsonPath("$.performerName").value("Ivan Petrov"));
    }

    @Test
    void performerOptionsReturnsDepartmentFilteredPerformers() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(service.performerOptions(departmentId)).thenReturn(List.of(
                new WorkOrderPerformerOptionDto(performerId, "Ivan Petrov", departmentId, "Maintenance", "MECHANIC")
        ));

        mockMvc.perform(get("/api/v1/work-orders/options/performers")
                        .param("departmentId", departmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(performerId.toString()))
                .andExpect(jsonPath("$[0].name").value("Ivan Petrov"))
                .andExpect(jsonPath("$[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$[0].departmentName").value("Maintenance"))
                .andExpect(jsonPath("$[0].role").value("MECHANIC"));

        verify(service).performerOptions(departmentId);
    }

    @Test
    void responseWithoutLinksReturnsNullObjects() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(workOrderId, null, null);
        when(service.findById(workOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequest").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.defect").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void detailResponseIncludesOperationsMaterialsCounts() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.PLANNED,
                repairRequestBrief(),
                defectBrief(),
                2,
                4
        );
        when(service.findById(workOrderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationsCount").value(2))
                .andExpect(jsonPath("$.materialsCount").value(4));
    }

    @Test
    void approveReturnsUpdatedApprovedWorkOrder() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.APPROVED,
                null,
                null
        );
        when(service.approve(workOrderId, approverId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/approve", workOrderId)
                        .param("approverId", approverId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workOrderId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(service).approve(workOrderId, approverId);
    }

    @Test
    void approveAlreadyApprovedWorkOrderReturns400() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrderEntity(workOrderId, UUID.randomUUID(), WorkOrderStatus.APPROVED)));
        when(service.approve(workOrderId, approverId))
                .thenThrow(RestException.badRequest("Only DRAFT/PLANNED work orders can be approved"));

        mockMvc.perform(post("/api/v1/work-orders/{id}/approve", workOrderId)
                        .param("approverId", approverId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only DRAFT/PLANNED work orders can be approved"));
    }

    @Test
    void listWithBlankSearchReturns200() throws Exception {
        WorkOrderDto dto = workOrderDto(UUID.randomUUID(), null, null);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, "", null, null))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(dto.id().toString()));
    }

    @Test
    void listForwardsPlannedDateRangeFilters() throws Exception {
        Instant plannedFrom = Instant.parse("2026-06-09T19:00:00Z");
        Instant plannedTo = Instant.parse("2026-06-10T19:00:00Z");
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 20, "pump", plannedFrom, plannedTo))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "20")
                        .param("search", "pump")
                        .param("plannedFrom", plannedFrom.toString())
                        .param("plannedTo", plannedTo.toString()))
                .andExpect(status().isOk());

        verify(service).search(null, null, null, 0, 20, "pump", plannedFrom, plannedTo);
    }

    @Test
    void calendarSummaryReturnsWorkOrderBuckets() throws Exception {
        WorkOrderCalendarSummaryResponse response = new WorkOrderCalendarSummaryResponse(
                2026,
                6,
                4,
                List.of(new WorkOrderStatusCountDto(WorkOrderStatus.APPROVED, 4)),
                List.of(),
                List.of(new WorkOrderCalendarBucketDto(
                        null,
                        LocalDate.of(2026, 6, 10),
                        4,
                        List.of(new WorkOrderStatusCountDto(WorkOrderStatus.APPROVED, 4)))));
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.calendarSummary(null, null, null, "pump", 2026, 6)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work-orders/calendar-summary")
                        .param("year", "2026")
                        .param("month", "6")
                        .param("search", "pump"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(6))
                .andExpect(jsonPath("$.totalOrders").value(4))
                .andExpect(jsonPath("$.statusCounts[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.days[0].date").value("2026-06-10"))
                .andExpect(jsonPath("$.days[0].totalOrders").value(4));

        verify(service).calendarSummary(null, null, null, "pump", 2026, 6);
    }

    @Test
    void listResponseIncludesOperationsMaterialsCounts() throws Exception {
        WorkOrderDto dto = workOrderDto(
                UUID.randomUUID(),
                WorkOrderStatus.PLANNED,
                null,
                null,
                3,
                1
        );
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, "", null, null))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].operationsCount").value(3))
                .andExpect(jsonPath("$.content[0].materialsCount").value(1));
    }

    @Test
    void listResponseWorksWithoutPerformer() throws Exception {
        WorkOrderDto dto = workOrderDto(UUID.randomUUID(), null, null);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, "", null, null))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(dto.id().toString()))
                .andExpect(jsonPath("$.content[0].performerId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].performerName").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void listResponseIncludesPerformerDetails() throws Exception {
        UUID performerId = UUID.randomUUID();
        WorkOrderDto dto = workOrderDtoWithPerformer(UUID.randomUUID(), performerId, "Ivan Petrov");
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, "", null, null))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].performerId").value(performerId.toString()))
                .andExpect(jsonPath("$.content[0].performerName").value("Ivan Petrov"));
    }

    @Test
    void listWithMissingLinkedRepairRequestReturnsNullObject() throws Exception {
        UUID missingRepairRequestId = UUID.randomUUID();
        WorkOrderDto dto = workOrderDtoWithIds(UUID.randomUUID(), missingRepairRequestId, null, null, null);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, "", null, null))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].repairRequestId").value(missingRepairRequestId.toString()))
                .andExpect(jsonPath("$.content[0].repairRequest").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void listWithMissingLinkedDefectReturnsNullObject() throws Exception {
        UUID missingDefectId = UUID.randomUUID();
        WorkOrderDto dto = workOrderDtoWithIds(UUID.randomUUID(), null, missingDefectId, null, null);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, "", null, null))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].defectId").value(missingDefectId.toString()))
                .andExpect(jsonPath("$.content[0].defect").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void listWithNoDataReturnsStablePage() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.search(null, null, null, 0, 10, "", null, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("search", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void startResponseShowsSyncedRepairRequestStatus() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.IN_PROGRESS,
                repairRequestBrief(RequestStatus.IN_PROGRESS),
                defectBrief(DefectStatus.OPEN)
        );
        when(service.start(workOrderId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/start", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.repairRequest.status").value("IN_PROGRESS"));
    }

    @Test
    void startResponseShowsSyncedDefectStatus() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.IN_PROGRESS,
                repairRequestBrief(RequestStatus.OPEN),
                defectBrief(DefectStatus.IN_PROGRESS)
        );
        when(service.start(workOrderId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/start", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.defect.status").value("IN_PROGRESS"));
    }

    @Test
    void completeResponseShowsResolvedDefectWhenEligible() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.COMPLETED,
                repairRequestBrief(RequestStatus.OPEN),
                defectBrief(DefectStatus.RESOLVED)
        );
        when(service.complete(eq(workOrderId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "done",
                                  "summary": "summary"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.defect.status").value("RESOLVED"));
    }

    @Test
    void closeResponseShowsClosedRepairRequestWhenEligible() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderDto response = workOrderDto(
                workOrderId,
                WorkOrderStatus.CLOSED,
                repairRequestBrief(RequestStatus.CLOSED),
                defectBrief(DefectStatus.CLOSED)
        );
        when(service.close(eq(workOrderId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/close", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "closed",
                                  "closureNotes": "notes"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.repairRequest.status").value("CLOSED"));
    }

    @Test
    void completeReplacementWithoutOldEquipmentReturnWarehouseIdReturnsBadRequest() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(service.complete(eq(workOrderId), any()))
                .thenThrow(RestException.badRequest("oldEquipmentReturnWarehouseId is required when workType is REPLACEMENT"));

        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "done",
                                  "summary": "summary"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("oldEquipmentReturnWarehouseId is required when workType is REPLACEMENT"));
    }

    @Test
    void completeNonReplacementWithOldEquipmentReturnWarehouseIdReturnsBadRequest() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(service.complete(eq(workOrderId), any()))
                .thenThrow(RestException.badRequest("oldEquipmentReturnWarehouseId must be null when workType is not REPLACEMENT"));

        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "done",
                                  "summary": "summary",
                                  "oldEquipmentReturnWarehouseId": "%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("oldEquipmentReturnWarehouseId must be null when workType is not REPLACEMENT"));
    }

    @Test
    void completeReplacementWithOldEquipmentReturnWarehouseIdReturnsOk() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID returnWarehouseId = UUID.randomUUID();
        WorkOrderDto response = new WorkOrderDto(
                workOrderId,
                "WO-2026-0001",
                "Replacement work order",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                null,
                WorkOrderStatus.COMPLETED,
                WorkOrderType.PLANNED,
                WorkType.REPLACEMENT,
                null,
                null,
                null,
                null,
                null,
                "summary",
                "done",
                null,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Replacement Equipment",
                List.of(),
                null,
                null,
                0,
                0
        );
        when(service.complete(eq(workOrderId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "done",
                                  "summary": "summary",
                                  "oldEquipmentReturnWarehouseId": "%s"
                                }
                                """.formatted(returnWarehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workOrderId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.workType").value("REPLACEMENT"));
    }

    private WorkOrder workOrderEntity(UUID id, UUID departmentId) {
        return workOrderEntity(id, departmentId, WorkOrderStatus.PLANNED);
    }

    private WorkOrder workOrderEntity(UUID id, UUID departmentId, WorkOrderStatus status) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setNumber("WO-2026-1001");
        workOrder.setTitle("Planned repair");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(departmentId);
        workOrder.setStatus(status);
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedById(UUID.randomUUID());
        return workOrder;
    }

    private String baseCreateRequestJson(UUID repairRequestId, UUID defectId) {
        return baseCreateRequestJson(repairRequestId, defectId, null);
    }

    private String baseCreateRequestJson(WorkOrderType type, UUID repairRequestId, UUID defectId) {
        return baseCreateRequestJson(type, repairRequestId, defectId, null);
    }

    private String baseCreateRequestJson(UUID repairRequestId, UUID defectId, UUID equipmentNodeId) {
        return baseCreateRequestJson(WorkOrderType.PLANNED, repairRequestId, defectId, equipmentNodeId);
    }

    private String baseCreateRequestJsonWithPerformer(UUID performerId) {
        return baseCreateRequestJson(WorkOrderType.PLANNED, null, null, null, performerId);
    }

    private String baseCreateRequestJson(
            WorkOrderType type,
            UUID repairRequestId,
            UUID defectId,
            UUID equipmentNodeId
    ) {
        return baseCreateRequestJson(type, repairRequestId, defectId, equipmentNodeId, null);
    }

    private String baseCreateRequestJson(
            WorkOrderType type,
            UUID repairRequestId,
            UUID defectId,
            UUID equipmentNodeId,
            UUID performerId
    ) {
        String repairRequestPart = repairRequestId == null
                ? ""
                : """
                  "repairRequestId": "%s",
                """.formatted(repairRequestId);
        String defectPart = defectId == null
                ? ""
                : """
                  "defectId": "%s",
                """.formatted(defectId);
        String equipmentNodePart = equipmentNodeId == null
                ? ""
                : """
                  "equipmentNodeId": "%s",
                """.formatted(equipmentNodeId);
        String performerPart = performerId == null
                ? ""
                : """
                  "performerId": "%s",
                """.formatted(performerId);
        return """
                {
                  "number": "WO-2026-1001",
                  "title": "Planned repair",
                  "equipmentId": "%s",
                %s
                  "departmentId": "%s",
                %s
                %s
                %s
                  "type": "%s",
                  "workType": "REPAIR",
                  "priority": "MEDIUM",
                  "createdById": "%s",
                  "summary": "summary"
                }
                """.formatted(
                UUID.randomUUID(),
                equipmentNodePart,
                UUID.randomUUID(),
                repairRequestPart,
                defectPart,
                performerPart,
                type.name(),
                UUID.randomUUID()
        );
    }

    private static class TestCurrentUserResolver implements HandlerMethodArgumentResolver {
        private final UUID userId;

        private TestCurrentUserResolver(UUID userId) {
            this.userId = userId;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, "USER", List.of());
        }
    }

    private WorkOrderDto workOrderDto(UUID id, RepairRequestBriefDto repairRequest, DefectBriefDto defect) {
        return workOrderDto(id, WorkOrderStatus.PLANNED, repairRequest, defect);
    }

    private WorkOrderDto workOrderDto(UUID id,
                                      WorkOrderStatus status,
                                      RepairRequestBriefDto repairRequest,
                                      DefectBriefDto defect) {
        return workOrderDto(id, status, repairRequest, defect, 0, 0);
    }

    private WorkOrderDto workOrderDto(UUID id,
                                      WorkOrderStatus status,
                                      RepairRequestBriefDto repairRequest,
                                      DefectBriefDto defect,
                                      int operationsCount,
                                      int materialsCount) {
        return new WorkOrderDto(
                id,
                "WO-2026-1001",
                "Planned repair",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Pump #1",
                "Maintenance",
                repairRequest == null ? null : repairRequest.id(),
                defect == null ? null : defect.id(),
                null,
                null,
                status,
                WorkOrderType.PLANNED,
                WorkType.REPAIR,
                PriorityLevel.MEDIUM,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                "summary",
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                List.of(),
                repairRequest,
                defect,
                operationsCount,
                materialsCount
        );
    }

    private WorkOrderDocumentDto workOrderDocumentDto(UUID workOrderId, String documentName) {
        return workOrderDocumentDto(workOrderId, UUID.randomUUID(), documentName, documentName + ".pdf", "application/pdf");
    }

    private WorkOrderDocumentDto workOrderDocumentDto(
            UUID workOrderId,
            UUID documentId,
            String documentName,
            String originalName,
            String contentType
    ) {
        UUID fileId = UUID.randomUUID();
        String downloadUrl = "/api/v1/work-orders/" + workOrderId + "/documents/" + documentId + "/download";
        return new WorkOrderDocumentDto(
                documentId,
                fileId,
                "ACT",
                documentName,
                originalName,
                contentType,
                128L,
                downloadUrl,
                "/api/v1/work-orders/" + workOrderId + "/documents/" + documentId + "/presigned-url",
                java.time.LocalDateTime.parse("2026-06-09T10:00:00"),
                java.time.LocalDateTime.parse("2026-06-09T10:00:00"),
                workOrderId,
                currentUserId,
                documentName,
                "ACT",
                java.time.LocalDateTime.parse("2026-06-09T10:00:00"),
                new WorkOrderDocumentDto.FileRef(
                        fileId,
                        originalName,
                        originalName,
                        contentType,
                        128L,
                        downloadUrl
                )
        );
    }

    private WorkOrderDto workOrderDtoWithIds(UUID id,
                                             UUID repairRequestId,
                                             UUID defectId,
                                             RepairRequestBriefDto repairRequest,
                                             DefectBriefDto defect) {
        return new WorkOrderDto(
                id,
                "WO-2026-1001",
                "Planned repair",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Pump #1",
                "Maintenance",
                repairRequestId,
                defectId,
                null,
                null,
                WorkOrderStatus.PLANNED,
                WorkOrderType.PLANNED,
                WorkType.REPAIR,
                PriorityLevel.MEDIUM,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                "summary",
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                List.of(),
                repairRequest,
                defect,
                0,
                0
        );
    }

    private WorkOrderDto workOrderDtoWithNode(UUID id, UUID equipmentNodeId) {
        return new WorkOrderDto(
                id,
                "WO-2026-1001",
                "Planned repair",
                UUID.randomUUID(),
                equipmentNodeId,
                "BRG-01",
                "Bearing",
                EquipmentNodeType.COMPONENT,
                UUID.randomUUID(),
                "Pump #1",
                "Maintenance",
                null,
                null,
                null,
                null,
                WorkOrderStatus.PLANNED,
                WorkOrderType.PLANNED,
                WorkType.REPAIR,
                PriorityLevel.MEDIUM,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                "summary",
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                0,
                0
        );
    }

    private WorkOrderDto workOrderDtoWithPerformer(UUID id, UUID performerId, String performerName) {
        return new WorkOrderDto(
                id,
                "WO-2026-1001",
                "Planned repair",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Pump #1",
                "Maintenance",
                null,
                null,
                null,
                null,
                performerId,
                performerName,
                WorkOrderStatus.PLANNED,
                WorkOrderType.PLANNED,
                WorkType.REPAIR,
                PriorityLevel.MEDIUM,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                "summary",
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                0,
                0
        );
    }

    private RepairRequestBriefDto repairRequestBrief() {
        return repairRequestBrief(RequestStatus.OPEN);
    }

    private RepairRequestBriefDto repairRequestBrief(RequestStatus status) {
        return new RepairRequestBriefDto(
                UUID.randomUUID(),
                "RR-2026-1001",
                status,
                PriorityLevel.MEDIUM,
                "Repair request",
                "Short description"
        );
    }

    private DefectBriefDto defectBrief() {
        return defectBrief(DefectStatus.OPEN);
    }

    private DefectBriefDto defectBrief(DefectStatus status) {
        return new DefectBriefDto(
                UUID.randomUUID(),
                "DEF-2026-1001",
                "Leak",
                status,
                "HIGH",
                Instant.now()
        );
    }

    @Test
    void statsShouldReturn200AndStatsPayload() throws Exception {
        com.toir.dto.workorder.WorkOrderStatsResponse statsResponse = new com.toir.dto.workorder.WorkOrderStatsResponse(10, 5, 4, 1);

        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(service.getStats(isNull(), isNull(), isNull(), isNull())).thenReturn(statsResponse);

        mockMvc.perform(get("/api/v1/work-orders/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(10))
                .andExpect(jsonPath("$.openOrders").value(5))
                .andExpect(jsonPath("$.completedOrders").value(4))
                .andExpect(jsonPath("$.overdueOrders").value(1));

        verify(service).getStats(null, null, null, null);
    }
}

