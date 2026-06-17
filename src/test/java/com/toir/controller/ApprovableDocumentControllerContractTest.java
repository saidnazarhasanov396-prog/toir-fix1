package com.toir.controller;

import com.toir.dto.approval.ApprovableDocumentDto;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.ApprovableDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApprovableDocumentControllerContractTest {

    @Mock
    ApprovableDocumentService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ApprovableDocumentController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listReturnsEmptyPage() throws Exception {
        when(service.search(isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/approvable-documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(service).search(null, null, null, 0, 20);
    }

    @Test
    void listForwardsSearchParamToService() throws Exception {
        when(service.search(eq("pump"), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/approvable-documents").param("search", "pump"))
                .andExpect(status().isOk());

        verify(service).search("pump", null, null, 0, 20);
    }

    @Test
    void listForwardsTypeParamToService() throws Exception {
        when(service.search(isNull(), eq(ApprovalTargetType.WORK_ORDER), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/approvable-documents").param("type", "WORK_ORDER"))
                .andExpect(status().isOk());

        verify(service).search(null, ApprovalTargetType.WORK_ORDER, null, 0, 20);
    }

    @Test
    void listForwardsStatusParamToService() throws Exception {
        when(service.search(isNull(), isNull(), eq(ApprovalStatus.PENDING), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/approvable-documents").param("status", "PENDING"))
                .andExpect(status().isOk());

        verify(service).search(null, null, ApprovalStatus.PENDING, 0, 20);
    }

    @Test
    void listForwardsPageAndSizeParamsToService() throws Exception {
        when(service.search(isNull(), isNull(), isNull(), eq(2), eq(50)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 50), 0));

        mockMvc.perform(get("/api/v1/approvable-documents")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(service).search(null, null, null, 2, 50);
    }

    @Test
    void listReturnsDocumentsInResponseContent() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-17T10:00:00Z");
        ApprovableDocumentDto document = new ApprovableDocumentDto(
                documentId,
                ApprovalTargetType.REPAIR_REQUEST,
                "RR-2026-0001",
                "Насос та'мирлаш",
                ApprovalStatus.PENDING,
                approvalId,
                createdAt
        );
        when(service.search(isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/approvable-documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(documentId.toString()))
                .andExpect(jsonPath("$.content[0].type").value("REPAIR_REQUEST"))
                .andExpect(jsonPath("$.content[0].code").value("RR-2026-0001"))
                .andExpect(jsonPath("$.content[0].name").value("Насос та'мирлаш"))
                .andExpect(jsonPath("$.content[0].approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].approvalId").value(approvalId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
