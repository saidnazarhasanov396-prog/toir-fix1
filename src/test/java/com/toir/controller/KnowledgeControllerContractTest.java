package com.toir.controller;

import com.toir.dto.knowledge.KnowledgeArticleDto;
import com.toir.dto.knowledge.KnowledgeArticleLinkDto;
import com.toir.dto.knowledge.KnowledgeContextResponse;
import com.toir.dto.knowledge.KnowledgeSuggestionDto;
import com.toir.entity.KnowledgeArticle;
import com.toir.enums.KnowledgeTargetType;
import com.toir.exception.RestException;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(MockitoExtension.class)
class KnowledgeControllerContractTest {

    @Mock
    KnowledgeService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithNoFiltersReturns200AndEmptyPage() throws Exception {
        when(service.list(isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(page(List.of(), 0, 10, 0));

        MvcResult result = mockMvc.perform(get("/api/v1/knowledge")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andReturn();

        assertNull(result.getResolvedException());
    }

    @Test
    void listWithEquipmentAndKindFiltersReturns200() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        Page<KnowledgeArticleDto> page = page(List.of(dto(equipmentId, "LESSON_LEARNED")), 0, 10, 1);
        when(service.list(eq(equipmentId), isNull(), eq("LESSON_LEARNED"), eq(0), eq(10)))
                .thenReturn(page);

        MvcResult result = mockMvc.perform(get("/api/v1/knowledge")
                        .param("equipmentId", equipmentId.toString())
                        .param("kind", "LESSON_LEARNED")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].tags").isArray())
                .andReturn();

        assertNull(result.getResolvedException());
    }

    @Test
    void listWithEquipmentTypeFilterReturns200() throws Exception {
        UUID equipmentTypeId = UUID.randomUUID();
        Page<KnowledgeArticleDto> page = page(List.of(dto(null, "PROCEDURE")), 0, 10, 1);
        when(service.list(isNull(), eq(equipmentTypeId), isNull(), eq(0), eq(10)))
                .thenReturn(page);

        MvcResult result = mockMvc.perform(get("/api/v1/knowledge")
                        .param("equipmentTypeId", equipmentTypeId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].kind").value("PROCEDURE"))
                .andReturn();

        assertNull(result.getResolvedException());
    }

    @Test
    void listWithBlankKindReturns200() throws Exception {
        when(service.list(isNull(), isNull(), eq("   "), eq(0), eq(10)))
                .thenReturn(page(List.of(), 0, 10, 0));

        mockMvc.perform(get("/api/v1/knowledge")
                        .param("kind", "   ")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(this::assertNoResolvedException)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listWithInvalidEquipmentIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge")
                        .param("equipmentId", "not-a-uuid")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createKnowledgeGeneratesCode() throws Exception {
        KnowledgeArticle created = articleEntity("LL-2026-0001", List.of("pump", "seal"));
        when(service.create(any(KnowledgeArticle.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "LL-001",
                                  "title": "Pump seal lesson",
                                  "kind": "LESSON_LEARNED",
                                  "problem": "Leakage",
                                  "rootCause": "Seal wear",
                                  "solution": "Replace seal",
                                  "tags": ["pump", "seal"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LL-2026-0001"))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags[0]").value("pump"))
                .andExpect(jsonPath("$.tags[1]").value("seal"));
    }

    @Test
    void createKnowledgeWithTagsStillReturnsTagsArray() throws Exception {
        KnowledgeArticle created = articleEntity("LL-2026-0002", List.of("pump", "seal"));
        when(service.create(any(KnowledgeArticle.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Another lesson",
                                  "kind": "LESSON_LEARNED",
                                  "problem": "Leakage",
                                  "rootCause": "Seal wear",
                                  "solution": "Replace seal",
                                  "tags": ["pump", "seal"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LL-2026-0002"))
                .andExpect(jsonPath("$.tags").isArray());
    }

    @Test
    void createKnowledgeSecondTimeDoesNotReuseLL001() throws Exception {
        KnowledgeArticle first = articleEntity("LL-2026-0003", List.of("pump"));
        KnowledgeArticle second = articleEntity("LL-2026-0004", List.of("pump"));
        when(service.create(any(KnowledgeArticle.class))).thenReturn(first, second);

        mockMvc.perform(post("/api/v1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "LL-001",
                                  "title": "Repeated payload",
                                  "kind": "LESSON_LEARNED",
                                  "problem": "Problem",
                                  "rootCause": "Cause",
                                  "solution": "Fix",
                                  "tags": ["pump"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LL-2026-0003"));

        mockMvc.perform(post("/api/v1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "LL-001",
                                  "title": "Repeated payload",
                                  "kind": "LESSON_LEARNED",
                                  "problem": "Problem",
                                  "rootCause": "Cause",
                                  "solution": "Fix",
                                  "tags": ["pump"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LL-2026-0004"));
    }

    @Test
    void createDuplicateCodeDoesNotReturn500() throws Exception {
        when(service.create(any(KnowledgeArticle.class)))
                .thenThrow(RestException.conflict("Article code already exists"));

        mockMvc.perform(post("/api/v1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "LL-001",
                                  "title": "Repeated payload",
                                  "kind": "LESSON_LEARNED",
                                  "problem": "Problem",
                                  "rootCause": "Cause",
                                  "solution": "Fix",
                                  "tags": ["pump"]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Article code already exists"));
    }

    @Test
    void contextReturnsLinkedAndSuggestedArticles() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        KnowledgeArticleLinkDto equipmentLink = new KnowledgeArticleLinkDto(
                UUID.randomUUID(),
                KnowledgeTargetType.EQUIPMENT,
                equipmentId);
        KnowledgeArticleDto linked = new KnowledgeArticleDto(
                UUID.randomUUID(),
                "LL-2026-0005",
                "Linked start procedure",
                "PROCEDURE",
                null,
                equipmentId,
                null,
                null,
                "Problem",
                "Cause",
                "Solution",
                "Preventive",
                List.of("start"),
                List.of(equipmentLink),
                null,
                0,
                Instant.now(),
                Instant.now(),
                false
        );
        KnowledgeArticleDto suggested = dto(null, "LESSON_LEARNED", "Similar lesson");
        when(service.context(KnowledgeTargetType.EQUIPMENT, equipmentId, 5))
                .thenReturn(new KnowledgeContextResponse(
                        List.of(linked),
                        List.of(new KnowledgeSuggestionDto(
                                suggested,
                                60,
                                List.of("Same equipment type")))));

        mockMvc.perform(get("/api/v1/knowledge/context")
                        .param("targetType", "EQUIPMENT")
                        .param("targetId", equipmentId.toString())
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked[0].title").value("Linked start procedure"))
                .andExpect(jsonPath("$.linked[0].links[0].targetType").value("EQUIPMENT"))
                .andExpect(jsonPath("$.suggestions[0].article.title").value("Similar lesson"))
                .andExpect(jsonPath("$.suggestions[0].score").value(60));
    }

    @Test
    void attachExistingArticleToTarget() throws Exception {
        UUID articleId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        KnowledgeArticleDto response = dto(null, "LESSON_LEARNED", "Attached article");
        when(service.link(eq(articleId), any(KnowledgeArticleLinkDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/knowledge/{id}/links", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "REPAIR_REQUEST",
                                  "targetId": "%s"
                                }
                                """.formatted(requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Attached article"));

        verify(service).link(eq(articleId), any(KnowledgeArticleLinkDto.class));
    }

    @Test
    void detachArticleFromTarget() throws Exception {
        UUID articleId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/knowledge/{id}/links", articleId)
                        .param("targetType", "WORK_ORDER")
                        .param("targetId", workOrderId.toString()))
                .andExpect(status().isNoContent());

        verify(service).unlink(articleId, KnowledgeTargetType.WORK_ORDER, workOrderId);
    }

    @Test
    void createKnowledgeWithNullTagsReturnsStableResponse() throws Exception {
        KnowledgeArticle created = articleEntity("KB-POST-2", List.of());
        when(service.create(any(KnowledgeArticle.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "KB-POST-2",
                                  "title": "Null tags lesson",
                                  "kind": "KB",
                                  "problem": "Problem",
                                  "rootCause": "Cause",
                                  "solution": "Fix",
                                  "tags": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("KB-POST-2"))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags.length()").value(0));
    }

    @Test
    void listReturnsUtf8CyrillicText() throws Exception {
        String expectedTitle = "Дефект насоса: перегрев";
        when(service.list(isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(page(List.of(dto(null, "LESSON_LEARNED", expectedTitle)), 0, 10, 1));

        MvcResult result = mockMvc.perform(get("/api/v1/knowledge")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].title").value(expectedTitle))
                .andReturn();

        String utf8Body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(utf8Body.contains(expectedTitle));
        assertFalse(utf8Body.contains("Ð"));
        assertNull(result.getResolvedException());
    }

    @Test
    void listReturnsTagsAsArrayNotString() throws Exception {
        KnowledgeArticleDto taggedDto = new KnowledgeArticleDto(
                UUID.randomUUID(),
                "KB-LIST-1",
                "List article",
                "KB",
                null,
                null,
                null,
                null,
                "Problem",
                "Cause",
                "Solution",
                "Preventive",
                List.of("pump", "seal"),
                null,
                0,
                Instant.now(),
                Instant.now(),
                false
        );
        when(service.list(isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(page(List.of(taggedDto), 0, 10, 1));

        mockMvc.perform(get("/api/v1/knowledge")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tags").isArray())
                .andExpect(jsonPath("$.content[0].tags[0]").value("pump"))
                .andExpect(jsonPath("$.content[0].tags[1]").value("seal"));
    }

    @Test
    void diagnosticOldUnpagedStubTriggers500WithUnsupportedOperationException() throws Exception {
        when(service.list(isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(dto(null, "LESSON_LEARNED"))));

        MvcResult result = mockMvc.perform(get("/api/v1/knowledge")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isInternalServerError())
                .andReturn();

        Exception ex = result.getResolvedException();
        assertNotNull(ex);
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        assertNotNull(root);
        System.out.println("Knowledge diagnostic exception class: " + ex.getClass().getName());
        System.out.println("Knowledge diagnostic exception message: " + ex.getMessage());
        System.out.println("Knowledge diagnostic root cause: " + root.getClass().getName() + " -> " + root.getMessage());
        assertEquals(UnsupportedOperationException.class, root.getClass());
    }

    private <T> Page<T> page(List<T> content, int page, int size, long total) {
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    private void assertNoResolvedException(MvcResult result) {
        Exception ex = result.getResolvedException();
        if (ex == null) {
            return;
        }
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String rootMessage = root.getClass().getName() + ": " + root.getMessage();
        fail("Resolved exception: " + ex.getClass().getName() + ": " + ex.getMessage() + "; root cause: " + rootMessage);
    }

    private KnowledgeArticleDto dto(UUID equipmentId, String kind) {
        return dto(equipmentId, kind, "Bearing failure lesson");
    }

    private KnowledgeArticleDto dto(UUID equipmentId, String kind, String title) {
        return new KnowledgeArticleDto(
                UUID.randomUUID(),
                "KB-01",
                title,
                kind,
                null,
                equipmentId,
                null,
                null,
                "Problem",
                "Cause",
                "Solution",
                "Preventive",
                List.of(),
                null,
                0,
                Instant.now(),
                Instant.now(),
                false
        );
    }

    @Test
    void statsShouldReturn200AndStatsPayload() throws Exception {
        com.toir.dto.knowledge.KnowledgeStatsResponse statsResponse = new com.toir.dto.knowledge.KnowledgeStatsResponse(10, 5, 3, 2);
        when(service.getStats(isNull(), isNull(), isNull())).thenReturn(statsResponse);

        mockMvc.perform(get("/api/v1/knowledge/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArticles").value(10))
                .andExpect(jsonPath("$.lessonLearned").value(5))
                .andExpect(jsonPath("$.procedures").value(3))
                .andExpect(jsonPath("$.troubleshooting").value(2));

        verify(service).getStats(null, null, null);
    }

    private KnowledgeArticle articleEntity(String code, List<String> tags) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setCode(code);
        article.setTitle("Article");
        article.setKind("LESSON_LEARNED");
        article.setProblem("Problem");
        article.setRootCause("Cause");
        article.setSolution("Solution");
        article.setTags(tags);
        return article;
    }
}
