package com.toir.controller;

import com.toir.dto.knowledge.KnowledgeArticleDto;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        String rootMessage = root != null ? root.getClass().getName() + ": " + root.getMessage() : "n/a";
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
}
