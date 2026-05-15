package com.toir.service;

import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import com.toir.entity.FileAsset;
import com.toir.entity.TechnicalDocument;
import com.toir.enums.DocumentType;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.TechnicalDocumentRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechnicalDocumentServiceTest {

    @Mock
    TechnicalDocumentRepository repository;

    @Mock
    FileAssetRepository fileAssetRepository;

    @Mock
    AuditBuilderService auditBuilderService;

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
