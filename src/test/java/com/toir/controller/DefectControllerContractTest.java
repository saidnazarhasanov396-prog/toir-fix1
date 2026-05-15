package com.toir.controller;

import com.toir.controller.defects.DefectController;
import com.toir.entity.KnowledgeArticle;
import com.toir.entity.defects.Defect;
import com.toir.exception.GlobalExceptionHandler;
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
    void createLessonFromDefectCreatesKnowledgeWithValidCyrillicTitle() throws Exception {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();

        Defect defect = Defect.builder()
                .code("DEF-001")
                .title("Перегрев подшипника")
                .description("Обнаружен рост температуры узла")
                .equipmentId(equipmentId)
                .workOrderId(workOrderId)
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
}
