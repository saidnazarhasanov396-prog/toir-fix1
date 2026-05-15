package com.toir.controller;

import com.toir.controller.defects.DefectController;
import com.toir.dto.defect.DefectRequest;
import com.toir.dto.defect.DefectResponse;
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
        mockMvc = MockMvcBuilders.standaloneSetup(new DefectController(service, defectRepository, knowledgeRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
                List.of()
        );
        when(service.findById(defectId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/defects/{id}", defectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairRequest").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.linkedWorkOrders").isArray())
                .andExpect(jsonPath("$.linkedWorkOrders").isEmpty());
    }

    @Test
    void createLessonFromDefectCreatesKnowledgeWithValidCyrillicTitle() throws Exception {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        Defect defect = Defect.builder()
                .code("DEF-001")
                .title("Перегрев подшипника")
                .description("Обнаружен рост температуры узла")
                .equipmentId(equipmentId)
                .failureReason("Недостаточная смазка")
                .build();
        defect.setId(defectId);

        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(knowledgeRepository.existsByCodeAndIsDeletedFalse("LL-DEF-DEF-001")).thenReturn(false);
        when(knowledgeRepository.save(any(KnowledgeArticle.class))).thenAnswer(invocation -> {
            KnowledgeArticle article = invocation.getArgument(0);
            article.setId(UUID.randomUUID());
            return article;
        });

        mockMvc.perform(post("/api/v1/defects/{id}/create-lesson", defectId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LL-DEF-DEF-001"))
                .andExpect(jsonPath("$.title", containsString("Дефект")))
                .andExpect(jsonPath("$.title", not(containsString("Ð"))))
                .andExpect(jsonPath("$.rootCause", containsString("Причина отказа")))
                .andExpect(jsonPath("$.rootCause", not(containsString("Ð"))))
                .andExpect(jsonPath("$.solution", not(containsString("Ð"))))
                .andExpect(jsonPath("$.preventiveActions", not(containsString("Ð"))));

        ArgumentCaptor<KnowledgeArticle> captor = ArgumentCaptor.forClass(KnowledgeArticle.class);
        verify(knowledgeRepository).save(captor.capture());
        KnowledgeArticle saved = captor.getValue();
        assertTrue(saved.getTitle().startsWith("Дефект "));
        assertFalse(saved.getTitle().contains("Ð"));
        assertEquals("Требуется заполнить по результатам расследования.", saved.getSolution());
        assertEquals("Требуется заполнить по результатам расследования.", saved.getPreventiveActions());
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
                )
        );
    }
}
