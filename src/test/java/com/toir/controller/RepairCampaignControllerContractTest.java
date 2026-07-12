package com.toir.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.controller.repair.RepairCampaignController;
import com.toir.dto.repaircampaign.RepairCampaignBudgetStageSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignBudgetSummaryDto;
import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.dto.repaircampaign.RepairCampaignWorkItemRequest;
import com.toir.dto.repaircampaign.RepairCampaignWorkItemResponse;
import com.toir.dto.repaircampaign.RepairCampaignShutdownLinkResponse;
import com.toir.enums.BudgetStatus;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.RepairCampaignWorkItemSourceType;
import com.toir.enums.RepairCampaignWorkItemStatus;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.ApprovalService;
import com.toir.service.repair.RepairCampaignService;
import com.toir.service.repair.RepairCampaignShutdownLinkService;
import com.toir.service.defects.DefectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RepairCampaignControllerContractTest {

    @Test
    void workItemEndpointsDeclareReadAndMutationPbac() {
        java.util.Map<String, String> expected = java.util.Map.of(
                "listWorkItems", "REPAIR_CAMPAIGN_READ",
                "addWorkItem", "REPAIR_CAMPAIGN_MANAGE_WORK",
                "updateWorkItem", "REPAIR_CAMPAIGN_MANAGE_WORK",
                "removeWorkItem", "REPAIR_CAMPAIGN_MANAGE_WORK",
                "reorderWorkItems", "REPAIR_CAMPAIGN_MANAGE_WORK");
        for (var method : RepairCampaignController.class.getDeclaredMethods()) {
            if (!expected.containsKey(method.getName())) continue;
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            assertThat(annotation).as(method.getName()).isNotNull();
            assertThat(annotation.value()).contains(expected.get(method.getName()));
        }
        assertThat(java.util.Arrays.stream(RepairCampaignController.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).filter(expected::containsKey)).hasSize(expected.size());
    }

    @Test
    void shutdownRelationshipEndpointsDeclareReadAndMutationPbac() {
        java.util.Map<String, String> expected = java.util.Map.of(
                "getShutdownLink", "REPAIR_CAMPAIGN_READ",
                "listShutdownLinks", "REPAIR_CAMPAIGN_READ",
                "linkShutdown", "REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS",
                "unlinkShutdown", "REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS",
                "listWorkItemWindows", "REPAIR_CAMPAIGN_READ",
                "addWorkItemWindow", "REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS",
                "removeWorkItemWindow", "REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS");
        for (var method : RepairCampaignController.class.getDeclaredMethods()) {
            if (!expected.containsKey(method.getName())) continue;
            assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
            assertThat(method.getAnnotation(PreAuthorize.class).value()).contains(expected.get(method.getName()));
        }
        assertThat(java.util.Arrays.stream(RepairCampaignController.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).filter(expected::containsKey)).hasSize(expected.size());
    }

    @Test
    void planningEndpointsDeclareReadAndMutationPbac() {
        java.util.Map<String, String> expected = java.util.Map.of(
                "listDependencies", "REPAIR_CAMPAIGN_READ",
                "addDependency", "REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES",
                "removeDependency", "REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES",
                "listResources", "REPAIR_CAMPAIGN_READ",
                "assignResource", "REPAIR_CAMPAIGN_MANAGE_RESOURCES",
                "removeResource", "REPAIR_CAMPAIGN_MANAGE_RESOURCES",
                "assessPlanning", "REPAIR_CAMPAIGN_READ");
        for (var method : RepairCampaignController.class.getDeclaredMethods()) {
            if (!expected.containsKey(method.getName())) continue;
            assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
            assertThat(method.getAnnotation(PreAuthorize.class).value()).contains(expected.get(method.getName()));
        }
        assertThat(java.util.Arrays.stream(RepairCampaignController.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).filter(expected::containsKey)).hasSize(expected.size());
    }

    @Test
    void materialEndpointsDeclareReadAndMutationPbac() {
        java.util.Map<String,String> expected=java.util.Map.of("listMaterials","REPAIR_CAMPAIGN_READ","addMaterial","REPAIR_CAMPAIGN_MANAGE_MATERIALS","updateMaterial","REPAIR_CAMPAIGN_MANAGE_MATERIALS","removeMaterial","REPAIR_CAMPAIGN_MANAGE_MATERIALS");
        for(var method:RepairCampaignController.class.getDeclaredMethods()){if(!expected.containsKey(method.getName()))continue;assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();assertThat(method.getAnnotation(PreAuthorize.class).value()).contains(expected.get(method.getName()));}
        assertThat(java.util.Arrays.stream(RepairCampaignController.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).filter(expected::containsKey)).hasSize(expected.size());
    }

    @Test
    void materialRemovalInUseIsRenderedAsTypedConflict() throws Exception {
        UUID campaignId=UUID.randomUUID(),materialId=UUID.randomUUID();
        when(materialService.remove(campaignId,materialId,3L))
                .thenThrow(RestException.conflict("RC_MATERIAL_REQUIREMENT_IN_USE"));
        mockMvc.perform(delete("/api/v1/repair-campaigns/{id}/materials/{materialId}",campaignId,materialId)
                        .param("version","3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("RC_MATERIAL_REQUIREMENT_IN_USE"))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void listShutdownLinksDelegatesVersionAndReturnsPagedTypedRelationships() throws Exception {
        UUID campaignId = UUID.randomUUID(); UUID shutdownId = UUID.randomUUID(); UUID linkId = UUID.randomUUID();
        when(shutdownLinkService.listForCampaign(campaignId, 7L)).thenReturn(List.of(
                new RepairCampaignShutdownLinkResponse(linkId, campaignId, shutdownId, 7L, 9L, true)));

        mockMvc.perform(get("/api/v1/repair-campaigns/{id}/planned-shutdowns", campaignId)
                        .param("version", "7").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(linkId.toString()))
                .andExpect(jsonPath("$.content[0].plannedShutdownId").value(shutdownId.toString()))
                .andExpect(jsonPath("$.content[0].plannedShutdownVersion").value(9));
        verify(shutdownLinkService).listForCampaign(campaignId, 7L);
    }

    @Test
    void addWorkItemIgnoresClientAttemptToSetServerOwnedStatus() throws Exception {
        UUID campaignId = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID(); UUID itemId = UUID.randomUUID();
        when(workItemService.add(eq(campaignId), any(RepairCampaignWorkItemRequest.class)))
                .thenReturn(new RepairCampaignWorkItemResponse(itemId, campaignId,
                        RepairCampaignWorkItemSourceType.MANUAL, null, equipmentId, "manual",
                        RepairCampaignWorkItemStatus.PENDING, 0, null, 2L));

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/work-items", campaignId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1,"sourceType":"MANUAL","sourceId":null,"equipmentId":"%s",
                                 "title":"manual","status":"COMPLETED","orderNumber":0,"notes":null}
                                """.formatted(equipmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        var captor = org.mockito.ArgumentCaptor.forClass(RepairCampaignWorkItemRequest.class);
        verify(workItemService).add(eq(campaignId), captor.capture());
        assertThat(java.util.Arrays.stream(captor.getValue().getClass().getRecordComponents())
                .map(component -> component.getName())).doesNotContain("status");
    }

    @Mock
    private RepairCampaignService service;

    @Mock
    private com.toir.service.repair.RepairCampaignWorkItemService workItemService;

    @Mock
    private RepairCampaignShutdownLinkService shutdownLinkService;
    @Mock
    private com.toir.service.repair.RepairCampaignMaterialService materialService;
    @Mock
    private com.toir.service.repair.RepairCampaignMutationImpactService mutationImpactService;

    @Mock
    private DefectService defectService;

    @Mock
    private ApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new RepairCampaignController(service, workItemService, shutdownLinkService, materialService, mutationImpactService, defectService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void generateWorkOrdersRequiresIdempotencyKeyAndHandsItToService() throws Exception {
        UUID campaignId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/generate-work-orders", campaignId)
                        .contentType("application/json")
                        .content("{\"stageId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());

        when(service.generateWorkOrders(eq(campaignId), org.mockito.ArgumentMatchers.any(), eq("generation-1")))
                .thenReturn(List.of());
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/generate-work-orders", campaignId)
                        .header("Idempotency-Key", "generation-1")
                        .contentType("application/json")
                        .content("{\"stageId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());

        assertThat(mockingDetails(service).getInvocations())
                .anySatisfy(invocation -> assertThat(invocation.getArguments())
                        .containsExactly(campaignId, invocation.getArgument(1), "generation-1"));
    }

    @Test
    void closeAcceptsOptionalNotesBodyAndPreservesNoBodyCompatibility() throws Exception {
        UUID campaignId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/close", campaignId))
                .andExpect(status().isOk());
        verify(service).close(campaignId, null);

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/close", campaignId)
                        .contentType("application/json")
                        .content("{\"notes\":\"  completed on schedule  \"}"))
                .andExpect(status().isOk());
        verify(service).close(campaignId, "  completed on schedule  ");
    }

    @Test
    void campaignDefectsForwardsPaginationAndFilters() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(defectService.searchByRepairCampaign(campaignId, com.toir.enums.DefectStatus.OPEN, "HIGH", 1, 25))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(), org.springframework.data.domain.PageRequest.of(1, 25), 0));

        mockMvc.perform(get("/api/v1/repair-campaigns/{id}/defects", campaignId)
                        .param("status", "OPEN")
                        .param("severity", "HIGH")
                        .param("page", "1")
                        .param("size", "25"))
                .andExpect(status().isOk());

        verify(defectService).searchByRepairCampaign(campaignId, com.toir.enums.DefectStatus.OPEN, "HIGH", 1, 25);
    }

    @Test
    void manualCreatePreservesMaliciousCanonicalFieldsForServiceRejection() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID shutdownId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID forgedCampaignId = UUID.randomUUID();
        UUID forgedStageId = UUID.randomUUID();
        when(service.createWorkOrder(eq(campaignId), eq(stageId), any())).thenAnswer(invocation -> {
            com.toir.dto.workorder.WorkOrderRequest request = invocation.getArgument(2);
            assertThat(request.generationKey()).isEqualTo("PS:forged");
            assertThat(request.plannedShutdownId()).isEqualTo(shutdownId);
            assertThat(request.shutdownWorkItemId()).isEqualTo(workItemId);
            assertThat(request.repairCampaignId()).isEqualTo(forgedCampaignId);
            assertThat(request.repairCampaignStageId()).isEqualTo(forgedStageId);
            assertThat(request.requiresShutdown()).isFalse();
            assertThat(request.requiresIsolation()).isFalse();
            throw RestException.badRequest("SERVER_OWNED_WORK_ORDER_FIELDS_NOT_ALLOWED");
        });

        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/stages/{stageId}/work-orders", campaignId, stageId)
                        .contentType("application/json")
                        .content("""
                                {"title":"Forged","equipmentId":"%s","departmentId":"%s",
                                 "type":"OVERHAUL","workType":"REPAIR","generationKey":"PS:forged",
                                 "plannedShutdownId":"%s","shutdownWorkItemId":"%s",
                                 "repairCampaignId":"%s","repairCampaignStageId":"%s",
                                 "requiresShutdown":false,"requiresIsolation":false}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), shutdownId, workItemId,
                                        forgedCampaignId, forgedStageId)))
                .andExpect(status().isBadRequest());

        verify(service).createWorkOrder(eq(campaignId), eq(stageId), any());
    }

    @Test
    void listCampaignsWithFiltersReturnsFilteredPage() throws Exception {
        UUID campaignId = UUID.randomUUID();
        RepairCampaignDto dto = new RepairCampaignDto(
                campaignId,
                "RC-2026-001",
                "Annual campaign",
                UUID.randomUUID(),
                "Maintenance Dept",
                RepairCampaignStatus.DRAFT,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("50000.1234"),
                new BigDecimal("0.0000"),
                new BigDecimal("50000.1234"),
                "Scope details",
                "Notes",
                List.of(),
                "UZS"
        );

        when(service.findAllFiltered(
                eq("annual"),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 12, 31)),
                eq(RepairCampaignStatus.DRAFT)
        )).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/repair-campaigns")
                        .param("search", "annual")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-12-31")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(campaignId.toString()))
                .andExpect(jsonPath("$.content[0].code").value("RC-2026-001"))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.content[0].startDate").value("2026-01-01"))
                .andExpect(jsonPath("$.content[0].endDate").value("2026-12-31"))
                .andExpect(jsonPath("$.content[0].totalBudget").value("50000.1234"))
                .andExpect(jsonPath("$.content[0].currencyCode").value("UZS"))
                .andExpect(jsonPath("$.content[0].description").value("Scope details"));
    }

    @Test
    void createAcceptsCanonicalFourDecimalStringsAndRejectsJsonNumbers() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            com.toir.dto.repaircampaign.RepairCampaignRequest request = invocation.getArgument(0);
            assertThat(request.totalBudget()).isEqualByComparingTo("123456789.1234");
            assertThat(request.currencyCode()).isEqualTo("UZS");
            return null;
        });

        String canonical = """
                {"name":"Precision overhaul","startDate":"2026-01-01","endDate":"2026-02-01",
                 "totalBudget":"123456789.1234","currencyCode":"UZS"}
                """;
        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType("application/json")
                        .content(canonical))
                .andExpect(status().isCreated());

        String numeric = """
                {"name":"Precision overhaul","startDate":"2026-01-01","endDate":"2026-02-01",
                 "totalBudget":123.45,"currencyCode":"UZS"}
                """;
        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType("application/json")
                        .content(numeric))
                .andExpect(status().isBadRequest());

        String excessiveScale = """
                {"name":"Precision overhaul","startDate":"2026-01-01","endDate":"2026-02-01",
                 "totalBudget":"123.45678","currencyCode":"UZS"}
                """;
        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType("application/json")
                        .content(excessiveScale))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRequiresVersionButCreateRemainsCompatibleWithoutIt() throws Exception {
        UUID campaignId = UUID.randomUUID();
        String payload = """
                {"name":"Versioned","startDate":"2026-01-01","endDate":"2026-02-01",
                 "totalBudget":"100.0000","currencyCode":"UZS"}
                """;

        mockMvc.perform(put("/api/v1/repair-campaigns/{id}", campaignId)
                        .contentType("application/json").content(payload))
                .andExpect(status().isBadRequest());
        verify(service, never()).update(eq(campaignId), any());

        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType("application/json").content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    void foreignOwnerFailureIsGenericForbidden() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(service.update(eq(campaignId), any()))
                .thenThrow(new AccessDeniedException("Access denied by repair campaign scope"));

        mockMvc.perform(put("/api/v1/repair-campaigns/{id}", campaignId)
                        .contentType("application/json")
                        .content("""
                                {"version":1,"name":"Forbidden","startDate":"2026-01-01",
                                 "endDate":"2026-02-01","totalBudget":"100.0000","currencyCode":"UZS"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void aggregateOnlyStageStatusIsRejectedAtJsonBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/stages", UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"sequence":1,"name":"Invalid","startDate":"2026-01-01",
                                 "endDate":"2026-01-02","plannedCost":"1.0000","status":"SUSPENDED"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void campaignOwnedMoneyRejectsMoreThanFifteenIntegerDigits() throws Exception {
        String campaign = """
                {"name":"Oversized budget","startDate":"2026-01-01","endDate":"2026-02-01",
                 "totalBudget":"1234567890123456.0000","currencyCode":"UZS"}
                """;
        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType("application/json")
                        .content(campaign))
                .andExpect(status().isBadRequest());

        String participant = """
                {"name":"Oversized participant","startDate":"2026-01-01","endDate":"2026-02-01",
                 "totalBudget":"100","currencyCode":"UZS","participantDepartments":[
                   {"departmentId":"%s","role":"PARTICIPANT","plannedBudget":"1234567890123456.0000"}
                 ]}
                """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType("application/json")
                        .content(participant))
                .andExpect(status().isBadRequest());

        String stage = """
                {"sequence":1,"name":"Oversized stage","startDate":"2026-01-01",
                 "endDate":"2026-01-02","plannedCost":"1234567890123456.0000"}
                """;
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/stages", UUID.randomUUID())
                        .contentType("application/json")
                        .content(stage))
                .andExpect(status().isBadRequest());
    }

    @Test
    void currencyCodeMustBeUppercaseIso4217() throws Exception {
        for (String currency : List.of("uzs", "ZZZ")) {
            String body = """
                    {"name":"Currency boundary","startDate":"2026-01-01","endDate":"2026-02-01",
                     "totalBudget":"100.0000","currencyCode":"%s"}
                    """.formatted(currency);
            mockMvc.perform(post("/api/v1/repair-campaigns")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void explicitNullCampaignMoneyIsRejected() throws Exception {
        String nullTotalBudget = """
                {"name":"Null budget","startDate":"2026-01-01","endDate":"2026-02-01",
                 "totalBudget":null,"currencyCode":"UZS"}
                """;
        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType("application/json")
                        .content(nullTotalBudget))
                .andExpect(status().isBadRequest());

        String nullParticipantBudget = """
                {"name":"Null participant budget","startDate":"2026-01-01","endDate":"2026-02-01",
                 "totalBudget":"100","currencyCode":"UZS","participantDepartments":[
                   {"departmentId":"%s","role":"PARTICIPANT","plannedBudget":null}
                 ]}
                """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType("application/json")
                        .content(nullParticipantBudget))
                .andExpect(status().isBadRequest());

        String nullStagePlannedCost = """
                {"sequence":1,"name":"Null stage budget","startDate":"2026-01-01",
                 "endDate":"2026-01-02","plannedCost":null}
                """;
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/stages", UUID.randomUUID())
                        .contentType("application/json")
                        .content(nullStagePlannedCost))
                .andExpect(status().isBadRequest());
    }

    @Test
    void omittedCampaignMoneyKeepsExistingZeroDefaults() throws Exception {
        String omittedTotalBudget = """
                {"name":"Default budget","startDate":"2026-01-01","endDate":"2026-02-01",
                 "currencyCode":"UZS","participantDepartments":[
                   {"departmentId":"%s","role":"PARTICIPANT"}
                 ]}
                """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/repair-campaigns")
                        .contentType("application/json")
                        .content(omittedTotalBudget))
                .andExpect(status().isCreated());

        String omittedStagePlannedCost = """
                {"sequence":1,"name":"Default stage budget","startDate":"2026-01-01",
                 "endDate":"2026-01-02"}
                """;
        mockMvc.perform(post("/api/v1/repair-campaigns/{id}/stages", UUID.randomUUID())
                        .contentType("application/json")
                        .content(omittedStagePlannedCost))
                .andExpect(status().isCreated());
    }

    @Test
    void budgetSummaryReturnsBudgetIntegrationShape() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        when(service.budgetSummary(campaignId)).thenReturn(new RepairCampaignBudgetSummaryDto(
                campaignId,
                budgetId,
                BudgetStatus.APPROVED,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(400),
                BigDecimal.valueOf(75),
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(1200),
                BigDecimal.valueOf(3800),
                1,
                BigDecimal.valueOf(75),
                List.of(new RepairCampaignBudgetStageSummaryDto(
                        UUID.randomUUID(),
                        "Preparation",
                        budgetLineId,
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(120),
                        BigDecimal.valueOf(50),
                        BigDecimal.valueOf(500),
                        BigDecimal.valueOf(120),
                        BigDecimal.valueOf(380),
                        BigDecimal.valueOf(180)
                )),
                "UZS"
        ));

        mockMvc.perform(get("/api/v1/repair-campaigns/{id}/budget-summary", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.maintenanceBudgetId").value(budgetId.toString()))
                .andExpect(jsonPath("$.budgetStatus").value("APPROVED"))
                .andExpect(jsonPath("$.campaignApprovedActual").value("400"))
                .andExpect(jsonPath("$.unallocatedActualCostCount").value(1))
                .andExpect(jsonPath("$.stages[0].budgetLineId").value(budgetLineId.toString()));
    }
}
