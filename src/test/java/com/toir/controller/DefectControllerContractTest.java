package com.toir.controller;

import com.toir.controller.defects.DefectController;
import com.toir.dto.defect.DefectDto;
import com.toir.dto.defect.DefectRequest;
import com.toir.dto.defect.DefectResponse;
import com.toir.dto.defect.DefectStatsResponse;
import com.toir.dto.triad.RepairRequestBriefDto;
import com.toir.dto.triad.WorkOrderBriefDto;
import com.toir.entity.KnowledgeArticle;
import com.toir.entity.defects.Defect;
import com.toir.enums.DefectStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.service.defects.DefectService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DefectControllerContractTest {

    @Mock
    DefectService service;

    @Mock
    DefectRepository defectRepository;

    @Mock
    KnowledgeArticleRepository knowledgeRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DefectController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithRepairRequestIdReturnsOnlyMatchingDefects() throws Exception {
        UUID repairRequestId = UUID.randomUUID();
        DefectResponse response = defectResponse(UUID.randomUUID(), repairRequestId);
        when(service.search(null, repairRequestId, 0, 100, null))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/v1/defects")
                        .param("repairRequestId", repairRequestId.toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].repairRequestId").value(repairRequestId.toString()))
                .andExpect(jsonPath("$.content[0].requestId").value(repairRequestId.toString()));

        verify(service).search(null, repairRequestId, 0, 100, null);
    }

    @Test
    void listWithRepairRequestIdReturnsEmptyWhenNoMatches() throws Exception {
        UUID repairRequestId = UUID.randomUUID();
        when(service.search(null, repairRequestId, 0, 100, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(get("/api/v1/defects")
                        .param("repairRequestId", repairRequestId.toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listWithoutRepairRequestIdReturnsAllNonDeletedDefects() throws Exception {
        DefectResponse first = defectResponse(UUID.randomUUID(), UUID.randomUUID());
        DefectResponse second = defectResponse(UUID.randomUUID(), null);
        when(service.search(null, null, 0, 100, null))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 100), 2));

        mockMvc.perform(get("/api/v1/defects")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(first.id().toString()))
                .andExpect(jsonPath("$.content[1].id").value(second.id().toString()))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(service).search(null, null, 0, 100, null);
    }

    @Test
    void listWithLegacyRequestIdAliasReturnsOnlyMatchingDefects() throws Exception {
        UUID repairRequestId = UUID.randomUUID();
        DefectResponse response = defectResponse(UUID.randomUUID(), repairRequestId);
        when(service.search(null, repairRequestId, 0, 100, null))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/v1/defects")
                        .param("requestId", repairRequestId.toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].repairRequestId").value(repairRequestId.toString()))
                .andExpect(jsonPath("$.content[0].requestId").value(repairRequestId.toString()));

        verify(service).search(null, repairRequestId, 0, 100, null);
    }

    @Test
    void listWithConflictingRepairRequestIdAndRequestIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/defects")
                        .param("repairRequestId", UUID.randomUUID().toString())
                        .param("requestId", UUID.randomUUID().toString())
                        .param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("repairRequestId and requestId cannot both be provided with different values"));

        verifyNoInteractions(service);
    }

    @Test
    void createWithRepairRequestIdAccepted() throws Exception {
        UUID repairRequestId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(service.create(any())).thenReturn(defectResponse(equipmentId, repairRequestId));

        mockMvc.perform(post("/api/v1/defects")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Leak",
                                  "description": "Oil leak detected",
                                  "equipmentId": "%s",
                                  "repairRequestId": "%s",
                                  "category": "MECHANICAL",
                                  "severity": "MEDIUM",
                                  "failureReason": "Seal damage",
                                  "rootCause": "Aging"
                                }
                                """.formatted(equipmentId, repairRequestId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repairRequestId").value(repairRequestId.toString()))
                .andExpect(jsonPath("$.requestId").value(repairRequestId.toString()));

        ArgumentCaptor<DefectRequest> captor = ArgumentCaptor.forClass(DefectRequest.class);
        verify(service).create(captor.capture());
        assertEquals(repairRequestId, captor.getValue().repairRequestId());
    }

    @Test
    void createWithLegacyRequestIdAliasAccepted() throws Exception {
        UUID repairRequestId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(service.create(any())).thenReturn(defectResponse(equipmentId, repairRequestId));

        mockMvc.perform(post("/api/v1/defects")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Leak",
                                  "description": "Oil leak detected",
                                  "equipmentId": "%s",
                                  "requestId": "%s",
                                  "category": "MECHANICAL",
                                  "severity": "MEDIUM",
                                  "failureReason": "Seal damage",
                                  "rootCause": "Aging"
                                }
                                """.formatted(equipmentId, repairRequestId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repairRequestId").value(repairRequestId.toString()))
                .andExpect(jsonPath("$.requestId").value(repairRequestId.toString()));

        ArgumentCaptor<DefectRequest> captor = ArgumentCaptor.forClass(DefectRequest.class);
        verify(service).create(captor.capture());
        assertEquals(repairRequestId, captor.getValue().repairRequestId());
    }

    @Test
    void createWithUnknownRepairRequestReturns404() throws Exception {
        when(service.create(any())).thenThrow(RestException.notFound("Repair request not found: " + UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/defects")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Leak",
                                  "description": "Oil leak detected",
                                  "equipmentId": "%s",
                                  "repairRequestId": "%s",
                                  "category": "MECHANICAL",
                                  "severity": "MEDIUM",
                                  "failureReason": "Seal damage",
                                  "rootCause": "Aging"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Repair request not found")));
    }

    @Test
    void responseIncludesRepairRequestObject() throws Exception {
        UUID defectId = UUID.randomUUID();
        DefectResponse response = defectResponse(UUID.randomUUID(), UUID.randomUUID());
        when(service.findById(defectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/defects/{id}", defectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequest.id").value(response.repairRequest().id().toString()))
                .andExpect(jsonPath("$.repairRequest.number").value(response.repairRequest().number()))
                .andExpect(jsonPath("$.repairRequest.status").value(response.repairRequest().status().name()));
    }

    @Test
    void responseIncludesLinkedWorkOrders() throws Exception {
        UUID defectId = UUID.randomUUID();
        DefectResponse response = defectResponse(UUID.randomUUID(), UUID.randomUUID());
        when(service.findById(defectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/defects/{id}", defectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedWorkOrders[0].id").value(response.linkedWorkOrders().getFirst().id().toString()))
                .andExpect(jsonPath("$.linkedWorkOrders[0].number").value(response.linkedWorkOrders().getFirst().number()))
                .andExpect(jsonPath("$.linkedWorkOrders[0].status").value(response.linkedWorkOrders().getFirst().status().name()));
    }

    @Test
    void responseWithNoRepairRequestReturnsNullAndEmptyList() throws Exception {
        UUID defectId = UUID.randomUUID();
        DefectResponse response = new DefectResponse(
                defectId,
                "DEF-2026-0005",
                "Leak",
                "Oil leak detected",
                UUID.randomUUID(),
                null,
                null,
                null,
                "MECHANICAL",
                "LOW",
                "Wear",
                "Aging",
                DefectStatus.OPEN,
                Instant.now(),
                null,
                0,
                null,
                List.of(),
                false
        );
        when(service.findById(defectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/defects/{id}", defectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequest").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.linkedWorkOrders").isArray())
                .andExpect(jsonPath("$.linkedWorkOrders").isEmpty());
    }

    @Test
    void createLessonFromDefectReturnsCreatedKnowledgeArticle() throws Exception {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(UUID.randomUUID());
        article.setCode("LL-DEF-DEF-001");
        article.setTitle("Дефект DEF-001: Перегрев подшипника");
        article.setKind("LESSON_LEARNED");
        article.setEquipmentId(equipmentId);
        article.setDefectId(defectId);
        article.setProblem("Обнаружен рост температуры узла");
        article.setRootCause("Причина отказа: Недостаточная смазка");
        article.setSolution("Требуется заполнить по результатам расследования.");
        article.setPreventiveActions("Требуется заполнить по результатам расследования.");

        when(service.createLesson(defectId)).thenReturn(article);

        mockMvc.perform(post("/api/v1/defects/{id}/create-lesson", defectId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LL-DEF-DEF-001"))
                .andExpect(jsonPath("$.title", containsString("Дефект")))
                .andExpect(jsonPath("$.title", not(containsString("Ð"))))
                .andExpect(jsonPath("$.rootCause", containsString("Причина отказа")))
                .andExpect(jsonPath("$.rootCause", not(containsString("Ð"))))
                .andExpect(jsonPath("$.solution", not(containsString("Ð"))))
                .andExpect(jsonPath("$.preventiveActions", not(containsString("Ð"))));

        verify(service).createLesson(defectId);
    }

    private DefectResponse defectResponse(UUID equipmentId, UUID repairRequestId) {
        return new DefectResponse(
                UUID.randomUUID(),
                "DEF-2026-0001",
                "Leak",
                "Oil leak detected",
                equipmentId,
                null,
                repairRequestId,
                repairRequestId,
                "MECHANICAL",
                "MEDIUM",
                "Seal damage",
                "Aging",
                DefectStatus.OPEN,
                Instant.now(),
                null,
                0,
                new RepairRequestBriefDto(
                        repairRequestId,
                        "RR-2026-1001",
                        RequestStatus.OPEN,
                        PriorityLevel.MEDIUM,
                        "Repair request",
                        "Short description"
                ),
                List.of(
                        new WorkOrderBriefDto(
                                UUID.randomUUID(),
                                "WO-2026-1001",
                                WorkOrderStatus.APPROVED,
                                WorkType.REPAIR,
                                PriorityLevel.MEDIUM,
                                Instant.now(),
                                Instant.now().plusSeconds(3600)
                        )
                ), false
        );
    }

    @Test
    void statsWithoutFiltersReturnsDefectStats() throws Exception {
        DefectStatsResponse response = new DefectStatsResponse(
                50,
                8,
                2,
                0
        );

        when(service.getStats(null, null, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/defects/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDefects").value(50))
                .andExpect(jsonPath("$.open").value(8))
                .andExpect(jsonPath("$.resolved").value(2))
                .andExpect(jsonPath("$.withRecurrence").value(0));

        verify(service).getStats(null, null, null);
    }

    @Test
    void statsWithFiltersPassesEquipmentRepairRequestAndSearchToService() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();

        DefectStatsResponse response = new DefectStatsResponse(
                12,
                5,
                3,
                2
        );

        when(service.getStats(equipmentId, repairRequestId, "pump")).thenReturn(response);

        mockMvc.perform(get("/api/v1/defects/stats")
                        .param("equipmentId", equipmentId.toString())
                        .param("repairRequestId", repairRequestId.toString())
                        .param("search", "pump"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDefects").value(12))
                .andExpect(jsonPath("$.open").value(5))
                .andExpect(jsonPath("$.resolved").value(3))
                .andExpect(jsonPath("$.withRecurrence").value(2));

        verify(service).getStats(equipmentId, repairRequestId, "pump");
    }

    @Test
    void statsSupportsRequestIdAliasAsRepairRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();

        DefectStatsResponse response = new DefectStatsResponse(
                4,
                2,
                1,
                1
        );

        when(service.getStats(null, requestId, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/defects/stats")
                        .param("requestId", requestId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDefects").value(4))
                .andExpect(jsonPath("$.open").value(2))
                .andExpect(jsonPath("$.resolved").value(1))
                .andExpect(jsonPath("$.withRecurrence").value(1));

        verify(service).getStats(null, requestId, null);
    }

    @Test
    void statsWithDifferentRepairRequestIdAndRequestIdReturnsBadRequest() throws Exception {
        UUID repairRequestId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/defects/stats")
                        .param("repairRequestId", repairRequestId.toString())
                        .param("requestId", requestId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("repairRequestId and requestId cannot both be provided with different values"));
    }

    @Test
    void getReturnsHasLessonField() throws Exception {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        DefectDto dto = new DefectDto(
                defectId,
                "DEF-001",
                "Pump defect",
                "Pump problem",
                equipmentId,
                null,
                "MECHANICAL",
                "HIGH",
                "Wear",
                "Bearing wear",
                DefectStatus.OPEN,
                Instant.now(),
                null,
                0
        );

        DefectResponse response = DefectResponse.from(
                dto,
                "Pump A",
                null,
                List.of(),
                true
        );

        when(service.findById(defectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/defects/{id}", defectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(defectId.toString()))
                .andExpect(jsonPath("$.hasLesson").value(true));

        verify(service).findById(defectId);
    }

}
