package com.toir.security;

import com.toir.controller.KnowledgeController;
import com.toir.dto.knowledge.KnowledgeArticleDto;
import com.toir.dto.knowledge.KnowledgeStatsResponse;
import com.toir.entity.KnowledgeArticle;
import com.toir.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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

@WebMvcTest(controllers = KnowledgeController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacKnowledgeSecurityTest.SecurityBeans.class
})
class RbacKnowledgeSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    KnowledgeService knowledgeService;

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
    void unauthenticatedCannotReadKnowledge() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadKnowledge() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotReadKnowledgeStats() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadKnowledgeStats() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.KNOWLEDGE_READ)
    void knowledgeReadCanReadKnowledgeStats() throws Exception {
        when(knowledgeService.getStats(isNull(), isNull(), isNull()))
                .thenReturn(new KnowledgeStatsResponse(4, 2, 1, 1));

        mockMvc.perform(get("/api/v1/knowledge/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.KNOWLEDGE_READ)
    void knowledgeReadCanReadListAndDetail() throws Exception {
        UUID articleId = UUID.randomUUID();
        when(knowledgeService.list(isNull(), isNull(), isNull(), eq(0), eq(1)))
                .thenReturn(page(List.of(articleDto(articleId))));
        when(knowledgeService.get(articleId)).thenReturn(article(articleId));

        mockMvc.perform(get("/api/v1/knowledge?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/knowledge/{id}", articleId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadKnowledge() throws Exception {
        when(knowledgeService.list(isNull(), isNull(), isNull(), eq(0), eq(1)))
                .thenReturn(page(List.of()));

        mockMvc.perform(get("/api/v1/knowledge?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadKnowledge() throws Exception {
        when(knowledgeService.list(isNull(), isNull(), isNull(), eq(0), eq(1)))
                .thenReturn(page(List.of()));

        mockMvc.perform(get("/api/v1/knowledge?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.KNOWLEDGE_CREATE)
    void knowledgeCreateCanCreateArticle() throws Exception {
        when(knowledgeService.create(any(KnowledgeArticle.class))).thenReturn(article(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articlePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.KNOWLEDGE_UPDATE)
    void knowledgeUpdateCanUpdateArticle() throws Exception {
        UUID articleId = UUID.randomUUID();
        when(knowledgeService.update(eq(articleId), any())).thenReturn(article(articleId));

        mockMvc.perform(put("/api/v1/knowledge/{id}", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articlePayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.KNOWLEDGE_DELETE)
    void knowledgeDeleteCanDeleteArticle() throws Exception {
        mockMvc.perform(delete("/api/v1/knowledge/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.KNOWLEDGE_READ)
    void knowledgeReadCannotMutateKnowledge() throws Exception {
        UUID articleId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articlePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/knowledge/{id}", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articlePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/knowledge/{id}", articleId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotMutateKnowledge() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articlePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotMutateKnowledge() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articlePayload()))
                .andExpect(status().isForbidden());
    }

    private Page<KnowledgeArticleDto> page(List<KnowledgeArticleDto> content) {
        return new PageImpl<>(content, PageRequest.of(0, 1), content.size());
    }

    private KnowledgeArticleDto articleDto(UUID id) {
        return new KnowledgeArticleDto(
                id,
                "LL-2026-0001",
                "Pump lesson",
                "LESSON_LEARNED",
                null,
                null,
                null,
                null,
                "Problem",
                "Cause",
                "Solution",
                "Preventive",
                List.of("pump"),
                null,
                0,
                Instant.parse("2026-05-01T08:00:00Z"),
                Instant.parse("2026-05-01T08:00:00Z"),
                false
        );
    }

    private KnowledgeArticle article(UUID id) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(id);
        article.setCode("LL-2026-0001");
        article.setTitle("Pump lesson");
        article.setKind("LESSON_LEARNED");
        article.setProblem("Problem");
        article.setRootCause("Cause");
        article.setSolution("Solution");
        article.setTags(List.of("pump"));
        return article;
    }

    private String articlePayload() {
        return """
                {
                  "title": "Pump lesson",
                  "kind": "LESSON_LEARNED",
                  "problem": "Leakage",
                  "rootCause": "Seal wear",
                  "solution": "Replace seal",
                  "tags": ["pump"]
                }
                """;
    }
}
