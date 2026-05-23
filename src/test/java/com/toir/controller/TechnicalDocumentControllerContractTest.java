package com.toir.controller;

import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import com.toir.enums.DocumentType;
import com.toir.enums.EquipmentNodeType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.TechnicalDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TechnicalDocumentControllerContractTest {

    @Mock
    TechnicalDocumentService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TechnicalDocumentController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listDocumentsReturnsFileObjectWhenFileExists() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        TechnicalDocumentDto dto = dtoWithFile(equipmentId, fileId);
        when(service.findByEquipment(equipmentId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/documents", equipmentId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].file.id").value(fileId.toString()))
                .andExpect(jsonPath("$.content[0].file.fileName").value("stored-manual.pdf"))
                .andExpect(jsonPath("$.content[0].file.originalName").value("Manual.pdf"))
                .andExpect(jsonPath("$.content[0].file.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.content[0].file.sizeBytes").value(12345))
                .andExpect(jsonPath("$.content[0].file.downloadUrl")
                        .value("/api/v1/files/assets/" + fileId + "/download"));

        verify(service).findByEquipment(equipmentId);
    }

    @Test
    void listDocumentsKeepsFileIdForBackwardCompatibility() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(service.findByEquipment(equipmentId)).thenReturn(List.of(dtoWithFile(equipmentId, fileId)));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/documents", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fileId").value(fileId.toString()));
    }

    @Test
    void listDocumentsWithMissingFileReturnsFileNull() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        TechnicalDocumentDto dto = new TechnicalDocumentDto(
                UUID.randomUUID(),
                equipmentId,
                UUID.randomUUID(),
                null,
                "Drawing",
                "A",
                DocumentType.DRAWING,
                LocalDate.of(2026, 5, 15),
                UUID.randomUUID()
        );
        when(service.findByEquipment(equipmentId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/documents", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].file", nullValue()));
    }

    @Test
    void createDocument_acceptsEquipmentNodeId() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentNodeId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(service.create(org.mockito.ArgumentMatchers.eq(equipmentId), any()))
                .thenReturn(dtoWithNode(equipmentId, fileId, equipmentNodeId));

        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/documents", equipmentId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "fileAssetId": "%s",
                                  "equipmentNodeId": "%s",
                                  "title": "Bearing drawing",
                                  "revision": "R1",
                                  "documentType": "DRAWING",
                                  "documentDate": "2026-05-23"
                                }
                                """.formatted(fileId, equipmentNodeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipmentNodeId").value(equipmentNodeId.toString()));

        ArgumentCaptor<TechnicalDocumentDto> captor = ArgumentCaptor.forClass(TechnicalDocumentDto.class);
        verify(service).create(org.mockito.ArgumentMatchers.eq(equipmentId), captor.capture());
        assertEquals(equipmentNodeId, captor.getValue().equipmentNodeId());
        assertEquals(fileId, captor.getValue().fileId());
    }

    @Test
    void getDocuments_returnsEquipmentNodeReference() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID equipmentNodeId = UUID.randomUUID();
        when(service.findByEquipment(equipmentId)).thenReturn(List.of(dtoWithNode(equipmentId, fileId, equipmentNodeId)));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/documents", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentNodeId").value(equipmentNodeId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentNodeCode").value("BRG-01"))
                .andExpect(jsonPath("$.content[0].equipmentNodeName").value("Bearing"))
                .andExpect(jsonPath("$.content[0].equipmentNodeType").value("COMPONENT"));
    }

    @Test
    void getNodeDocuments_returnsPageOrList() throws Exception {
        UUID nodeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(service.findByEquipmentNode(nodeId)).thenReturn(List.of(dtoWithNode(equipmentId, fileId, nodeId)));

        mockMvc.perform(get("/api/v1/equipment-nodes/{nodeId}/documents", nodeId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].equipmentNodeId").value(nodeId.toString()));

        verify(service).findByEquipmentNode(nodeId);
    }

    private TechnicalDocumentDto dtoWithFile(UUID equipmentId, UUID fileId) {
        return new TechnicalDocumentDto(
                UUID.randomUUID(),
                equipmentId,
                fileId,
                new TechnicalDocumentDto.FileRef(
                        fileId,
                        "stored-manual.pdf",
                        "Manual.pdf",
                        "application/pdf",
                        12345,
                        "/api/v1/files/assets/" + fileId + "/download"
                ),
                "Manual",
                "1.0",
                DocumentType.MANUAL,
                LocalDate.of(2026, 5, 15),
                UUID.randomUUID()
        );
    }

    private TechnicalDocumentDto dtoWithNode(UUID equipmentId, UUID fileId, UUID equipmentNodeId) {
        return new TechnicalDocumentDto(
                UUID.randomUUID(),
                equipmentId,
                equipmentNodeId,
                fileId,
                new TechnicalDocumentDto.FileRef(
                        fileId,
                        "stored-drawing.pdf",
                        "Drawing.pdf",
                        "application/pdf",
                        12345,
                        "/api/v1/files/assets/" + fileId + "/download"
                ),
                "Bearing drawing",
                "R1",
                DocumentType.DRAWING,
                LocalDate.of(2026, 5, 23),
                UUID.randomUUID(),
                "BRG-01",
                "Bearing",
                EquipmentNodeType.COMPONENT
        );
    }
}
