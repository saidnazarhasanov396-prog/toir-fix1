package com.toir.service;

import com.toir.dto.mxik.MxikDto;
import com.toir.dto.mxik.MxikRequest;
import com.toir.entity.Mxik;
import com.toir.repository.MxikRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MxikServiceTest {

    @Mock
    MxikRepository repository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    MxikService service;

    @Test
    void createPersistsUzAssetsFields() {
        MxikRequest request = new MxikRequest(
                "Bearing",
                "12345678901234567",
                "SPARE_PART",
                "Mechanical",
                "Bearing position",
                "Bearing latn",
                "Bearing ru",
                "Mechanical ru",
                "Mechanical cyril",
                "Class A",
                "Class A ru",
                "Class A cyril",
                "Position ru",
                "Position cyril",
                "Sub position",
                "Sub position ru",
                "Sub position cyril",
                "SKF",
                "SKF ru",
                "SKF cyril",
                "Size",
                "Size ru",
                "Size cyril",
                "4607000000000"
        );
        when(repository.existsActiveByKod("12345678901234567")).thenReturn(false);
        when(repository.save(any(Mxik.class))).thenAnswer(invocation -> {
            Mxik mxik = invocation.getArgument(0);
            mxik.setId(UUID.randomUUID());
            return mxik;
        });

        MxikDto dto = service.create(request);

        ArgumentCaptor<Mxik> captor = ArgumentCaptor.forClass(Mxik.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getBarcode()).isEqualTo("4607000000000");
        assertThat(captor.getValue().getClassName()).isEqualTo("Class A");
        assertThat(dto.barcode()).isEqualTo("4607000000000");
        assertThat(dto.className()).isEqualTo("Class A");
    }
}
