package com.toir.controller;

import com.toir.dto.stockmovement.StockMovementFileDto;
import com.toir.dto.stockmovement.StockMovementDocumentDto;
import com.toir.enums.StockMovementType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUserArgumentResolver;
import com.toir.service.StockMovementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StockMovementControllerContractTest {

    @Mock
    StockMovementService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StockMovementController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadFilesAcceptsRepeatedFilesAndReturnsFileRefs() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        UUID invoiceFileId = UUID.randomUUID();
        UUID certificateFileId = UUID.randomUUID();
        authenticate(userId);
        when(service.movementType(movementId)).thenReturn(StockMovementType.RECEIPT);
        when(service.attachFiles(eq(movementId), any(), any()))
                .thenReturn(List.of(
                        stockMovementFile(invoiceFileId, "invoice.pdf", "application/pdf", 100L),
                        stockMovementFile(certificateFileId, "certificate.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 120L)
                ));

        mockMvc.perform(multipart("/api/v1/stock-movements/{movementId}/files", movementId)
                        .file(new MockMultipartFile("files", "invoice.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .file(new MockMultipartFile("files", "certificate.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(invoiceFileId.toString()))
                .andExpect(jsonPath("$[0].originalName").value("invoice.pdf"))
                .andExpect(jsonPath("$[1].id").value(certificateFileId.toString()))
                .andExpect(jsonPath("$[1].originalName").value("certificate.xlsx"));

        verify(service).attachFiles(eq(movementId), any(), any(AuthenticatedUser.class));
    }

    @Test
    void attachDocumentCreatesNamedMultiFileDocument() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID frontFileId = UUID.randomUUID();
        UUID backFileId = UUID.randomUUID();
        authenticate(userId);
        when(service.movementType(movementId)).thenReturn(StockMovementType.RECEIPT);
        when(service.attachDocument(
                eq(movementId),
                any(),
                eq("Invoice"),
                eq("RECEIPT_ACT"),
                eq("INV-2026-001"),
                any()
        )).thenReturn(stockMovementDocument(documentId, frontFileId, backFileId));

        mockMvc.perform(multipart("/api/v1/stock-movements/{movementId}/documents", movementId)
                        .file(new MockMultipartFile("files", "invoice-front.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .file(new MockMultipartFile("files", "invoice-back.pdf", "application/pdf", "%PDF-1.4\n".getBytes()))
                        .param("documentName", "Invoice")
                        .param("documentType", "RECEIPT_ACT")
                        .param("documentNumber", "INV-2026-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(documentId.toString()))
                .andExpect(jsonPath("$.documentName").value("Invoice"))
                .andExpect(jsonPath("$.documentType").value("RECEIPT_ACT"))
                .andExpect(jsonPath("$.documentNumber").value("INV-2026-001"))
                .andExpect(jsonPath("$.files.length()").value(2))
                .andExpect(jsonPath("$.files[0].id").value(frontFileId.toString()))
                .andExpect(jsonPath("$.files[1].id").value(backFileId.toString()));

        verify(service).attachDocument(
                eq(movementId),
                any(),
                eq("Invoice"),
                eq("RECEIPT_ACT"),
                eq("INV-2026-001"),
                any(AuthenticatedUser.class)
        );
    }

    @Test
    void listDownloadAndDeleteUseFileLevelEndpoints() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        authenticate(userId);
        when(service.movementType(movementId)).thenReturn(StockMovementType.RECEIPT);
        when(service.listFiles(eq(movementId), any()))
                .thenReturn(List.of(stockMovementFile(fileId, "invoice.pdf", "application/pdf", 100L)));
        when(service.getFile(eq(movementId), eq(fileId), any()))
                .thenReturn(stockMovementFile(fileId, "invoice.pdf", "application/pdf", 100L));
        when(service.downloadFile(eq(movementId), eq(fileId), any()))
                .thenReturn(new ByteArrayResource("pdf".getBytes()));

        mockMvc.perform(get("/api/v1/stock-movements/{movementId}/files", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(fileId.toString()));

        mockMvc.perform(get("/api/v1/stock-movements/{movementId}/files/{fileId}/download",
                        movementId, fileId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("invoice.pdf")));

        mockMvc.perform(delete("/api/v1/stock-movements/{movementId}/files/{fileId}", movementId, fileId))
                .andExpect(status().isNoContent());

        verify(service).deleteFile(eq(movementId), eq(fileId), any());
    }

    private StockMovementFileDto stockMovementFile(
            UUID id,
            String originalName,
            String contentType,
            long size
    ) {
        return new StockMovementFileDto(
                id,
                originalName,
                contentType,
                size,
                "/api/v1/stock-movements/movement-1/files/" + id + "/download"
        );
    }

    private StockMovementDocumentDto stockMovementDocument(UUID documentId, UUID frontFileId, UUID backFileId) {
        return new StockMovementDocumentDto(
                documentId,
                "Invoice",
                "RECEIPT_ACT",
                "INV-2026-001",
                java.time.LocalDateTime.now(),
                List.of(
                        stockMovementFile(frontFileId, "invoice-front.pdf", "application/pdf", 100L),
                        stockMovementFile(backFileId, "invoice-back.pdf", "application/pdf", 100L)
                )
        );
    }

    private void authenticate(UUID userId) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId.toString(),
                "user",
                "user@example.com",
                "User",
                null,
                "USER",
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        user,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("STOCK_READ"),
                                new SimpleGrantedAuthority("STOCK_RECEIVE")
                        )
                )
        );
    }
}
