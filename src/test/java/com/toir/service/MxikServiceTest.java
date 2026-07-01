package com.toir.service;

import com.toir.dto.mxik.MxikDto;
import com.toir.entity.Mxik;
import com.toir.repository.MxikRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MxikServiceTest {

    @Mock
    MxikRepository repository;

    @InjectMocks
    MxikService service;

    @Test
    void findByIdMapsUzAssetsFields() {
        UUID id = UUID.randomUUID();
        Mxik mxik = new Mxik();
        mxik.setId(id);
        mxik.setName("Bearing");
        mxik.setKod("12345678901234567");
        mxik.setType("SPARE_PART");
        mxik.setClassName("Class A");
        mxik.setBarcode("4607000000000");
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(mxik));

        MxikDto dto = service.findById(id);

        assertThat(dto.barcode()).isEqualTo("4607000000000");
        assertThat(dto.className()).isEqualTo("Class A");
    }
}
