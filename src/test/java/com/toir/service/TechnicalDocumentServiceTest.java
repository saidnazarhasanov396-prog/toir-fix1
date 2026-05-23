package com.toir.service;

import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import com.toir.entity.FileAsset;
import com.toir.entity.TechnicalDocument;
import com.toir.enums.DocumentType;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.enums.EquipmentNodeType;
import com.toir.exception.RestException;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.TechnicalDocumentRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TechnicalDocumentServiceTest {

    @Mock
    TechnicalDocumentRepository repository;

    @Mock
    FileAssetRepository fileAssetRepository;

    @Mock
    EquipmentNodeRepository equipmentNodeRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    EquipmentStatusLifecycleService equipmentStatusLifecycleService;

    @InjectMocks
    TechnicalDocumentService service;

    @Test
    void enrichesDocumentsWithFileMetadata() {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        TechnicalDocument document = document(equipmentId, fileId, "Manual");
        FileAsset fileAsset = fileAsset(fileId, "manual-store.pdf", "Manual.pdf", "application/pdf", 12345L);

        when(repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(document));
        when(fileAssetRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(fileAsset));

        List<TechnicalDocumentDto> result = service.findByEquipment(equipmentId);

        assertThat(result).hasSize(1);
        TechnicalDocumentDto dto = result.get(0);
        assertThat(dto.fileId()).isEqualTo(fileId);
        assertThat(dto.file()).isNotNull();
        assertThat(dto.file().id()).isEqualTo(fileId);
        assertThat(dto.file().fileName()).isEqualTo("manual-store.pdf");
        assertThat(dto.file().originalName()).isEqualTo("Manual.pdf");
        assertThat(dto.file().mimeType()).isEqualTo("application/pdf");
        assertThat(dto.file().sizeBytes()).isEqualTo(12345L);
        assertThat(dto.file().downloadUrl()).isEqualTo("/api/v1/files/assets/" + fileId + "/download");
    }

    @Test
    void missingDeletedFileDoesNotThrow() {
        UUID equipmentId = UUID.randomUUID();
        UUID missingFileId = UUID.randomUUID();
        TechnicalDocument document = document(equipmentId, missingFileId, "Certificate");

        when(repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(document));
        when(fileAssetRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());

        List<TechnicalDocumentDto> result = service.findByEquipment(equipmentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileId()).isEqualTo(missingFileId);
        assertThat(result.get(0).file()).isNull();
    }

    @Test
    void batchLoadsFileAssetsOnce() {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId1 = UUID.randomUUID();
        UUID fileId2 = UUID.randomUUID();

        TechnicalDocument d1 = document(equipmentId, fileId1, "Doc-1");
        TechnicalDocument d2 = document(equipmentId, fileId1, "Doc-2");
        TechnicalDocument d3 = document(equipmentId, fileId2, "Doc-3");

        when(repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(d1, d2, d3));
        when(fileAssetRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(fileAsset(fileId1, "f1", "o1", "application/pdf", 1L),
                        fileAsset(fileId2, "f2", "o2", "application/pdf", 2L)));

        service.findByEquipment(equipmentId);

        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(fileAssetRepository, times(1)).findAllByIdInAndIsDeletedFalse(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(fileId1, fileId2);
    }

    @Test
    void createDocument_withEquipmentNode_setsNodeTarget() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        TechnicalDocumentDto request = request(equipmentId, UUID.randomUUID(), nodeId);
        EquipmentNode node = equipmentNode(nodeId, equipmentId, "BRG-01", "Bearing", EquipmentNodeType.COMPONENT);
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.of(node));
        when(repository.save(any(TechnicalDocument.class))).thenAnswer(invocation -> {
            TechnicalDocument saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        TechnicalDocumentDto result = service.create(equipmentId, request);

        ArgumentCaptor<TechnicalDocument> captor = ArgumentCaptor.forClass(TechnicalDocument.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEquipmentNodeId()).isEqualTo(nodeId);
        assertThat(result.equipmentNodeId()).isEqualTo(nodeId);
    }

    @Test
    void createDocument_withNodeFromDifferentEquipment_returnsBadRequest() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId))
                .thenReturn(Optional.of(equipmentNode(nodeId, otherEquipmentId, "BRG-01", "Bearing", EquipmentNodeType.COMPONENT)));

        assertThatThrownBy(() -> service.create(equipmentId, request(equipmentId, UUID.randomUUID(), nodeId)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Equipment node belongs to a different equipment");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void createDocument_withoutNode_stillWorks() {
        UUID equipmentId = UUID.randomUUID();
        TechnicalDocumentDto request = request(equipmentId, UUID.randomUUID(), null);
        when(repository.save(any(TechnicalDocument.class))).thenAnswer(invocation -> {
            TechnicalDocument saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        TechnicalDocumentDto result = service.create(equipmentId, request);

        ArgumentCaptor<TechnicalDocument> captor = ArgumentCaptor.forClass(TechnicalDocument.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEquipmentNodeId()).isNull();
        assertThat(result.equipmentNodeId()).isNull();
    }

    @Test
    void listEquipmentDocuments_includesNodeReference() {
        UUID equipmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        TechnicalDocument document = document(equipmentId, fileId, "Drawing");
        document.setEquipmentNodeId(nodeId);
        EquipmentNode node = equipmentNode(nodeId, equipmentId, "BRG-01", "Bearing", EquipmentNodeType.COMPONENT);

        when(repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(document));
        when(fileAssetRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(fileAsset(fileId, "drawing.pdf", "Drawing.pdf", "application/pdf", 120L)));
        when(equipmentNodeRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(node));

        List<TechnicalDocumentDto> result = service.findByEquipment(equipmentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).equipmentNodeId()).isEqualTo(nodeId);
        assertThat(result.get(0).equipmentNodeCode()).isEqualTo("BRG-01");
        assertThat(result.get(0).equipmentNodeName()).isEqualTo("Bearing");
        assertThat(result.get(0).equipmentNodeType()).isEqualTo(EquipmentNodeType.COMPONENT);
    }

    @Test
    void listNodeDocuments_returnsOnlyNodeDocuments() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        TechnicalDocument document = document(equipmentId, fileId, "Certificate");
        document.setEquipmentNodeId(nodeId);
        EquipmentNode node = equipmentNode(nodeId, equipmentId, "BRG-01", "Bearing", EquipmentNodeType.COMPONENT);

        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.of(node));
        when(repository.findAllByEquipmentNodeIdAndIsDeletedFalse(nodeId)).thenReturn(List.of(document));
        when(fileAssetRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(fileAsset(fileId, "certificate.pdf", "Certificate.pdf", "application/pdf", 80L)));
        when(equipmentNodeRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(node));

        List<TechnicalDocumentDto> result = service.findByEquipmentNode(nodeId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).equipmentNodeId()).isEqualTo(nodeId);
        verify(repository).findAllByEquipmentNodeIdAndIsDeletedFalse(nodeId);
    }

    private TechnicalDocument document(UUID equipmentId, UUID fileId, String title) {
        TechnicalDocument document = new TechnicalDocument();
        document.setId(UUID.randomUUID());
        document.setEquipmentId(equipmentId);
        document.setFileId(fileId);
        document.setTitle(title);
        document.setType(DocumentType.MANUAL);
        document.setRevision("1.0");
        document.setDocumentDate(LocalDate.of(2026, 5, 15));
        return document;
    }

    private TechnicalDocumentDto request(UUID equipmentId, UUID fileId, UUID equipmentNodeId) {
        return new TechnicalDocumentDto(
                null,
                equipmentId,
                equipmentNodeId,
                fileId,
                null,
                "Manual",
                "R1",
                DocumentType.MANUAL,
                LocalDate.of(2026, 5, 23),
                UUID.randomUUID()
        );
    }

    private EquipmentNode equipmentNode(UUID id, UUID equipmentId, String code, String name, EquipmentNodeType nodeType) {
        EquipmentNode node = new EquipmentNode();
        node.setId(id);
        node.setEquipmentId(equipmentId);
        node.setCode(code);
        node.setName(name);
        node.setNodeType(nodeType);
        return node;
    }

    private FileAsset fileAsset(UUID id, String fileName, String originalName, String mimeType, long sizeBytes) {
        FileAsset fileAsset = new FileAsset();
        fileAsset.setId(id);
        fileAsset.setFileName(fileName);
        fileAsset.setOriginalName(originalName);
        fileAsset.setMimeType(mimeType);
        fileAsset.setSizeBytes(sizeBytes);
        return fileAsset;
    }
}
