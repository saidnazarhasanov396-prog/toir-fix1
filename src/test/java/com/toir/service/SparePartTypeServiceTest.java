package com.toir.service;

import com.toir.dto.spareparttype.SparePartTypeDto;
import com.toir.dto.spareparttype.SparePartTypeRequest;
import com.toir.entity.SparePartType;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.SparePartTypeRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparePartTypeServiceTest {

    @Mock
    SparePartTypeRepository repository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    SparePartTypeService service;

    @BeforeEach
    void setUp() {
        service = new SparePartTypeService(repository, sparePartRepository, auditBuilderService);
    }

    @Test
    void listReturnsOnlyActiveTypesByDefault() {
        SparePartType oil = type(UUID.randomUUID(), "OIL", "Oil", "LITER", true);
        when(repository.findAllActive(null)).thenReturn(List.of(oil));

        List<SparePartTypeDto> result = service.findAll(null, false);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().code()).isEqualTo("OIL");
        assertThat(result.getFirst().defaultUnit()).isEqualTo("LITER");
        verify(repository).findAllActive(null);
    }

    @Test
    void createRejectsDuplicateCodeIgnoringCase() {
        when(repository.existsByCodeIgnoreCase("OIL")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SparePartTypeRequest(
                "oil",
                "Oil",
                "Lubricants",
                "LITER",
                true
        ))).isInstanceOfSatisfying(RestException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains("code");
        });

        verify(repository, never()).save(any());
    }

    @Test
    void createNormalizesCodeAndDefaultsActive() {
        when(repository.existsByCodeIgnoreCase("ELECTRICAL_PART")).thenReturn(false);
        when(repository.save(any(SparePartType.class))).thenAnswer(invocation -> {
            SparePartType saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.create(new SparePartTypeRequest(
                " electrical part ",
                "Electrical Part",
                null,
                "PCS",
                null
        ));

        ArgumentCaptor<SparePartType> captor = ArgumentCaptor.forClass(SparePartType.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("ELECTRICAL_PART");
        assertThat(captor.getValue().getName()).isEqualTo("Electrical Part");
        assertThat(captor.getValue().getDefaultUnit()).isEqualTo("PCS");
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void deleteReferencedTypeMarksItInactiveInsteadOfRemovingIt() {
        UUID typeId = UUID.randomUUID();
        SparePartType oil = type(typeId, "OIL", "Oil", "LITER", true);

        when(repository.findById(typeId)).thenReturn(Optional.of(oil));
        when(sparePartRepository.existsByTypeIdAndIsDeletedFalse(typeId)).thenReturn(true);

        service.delete(typeId);

        assertThat(oil.getActive()).isFalse();
        verify(repository).save(oil);
        verify(repository, never()).delete(oil);
    }

    private SparePartType type(UUID id, String code, String name, String defaultUnit, boolean active) {
        SparePartType type = new SparePartType();
        type.setId(id);
        type.setCode(code);
        type.setName(name);
        type.setDefaultUnit(defaultUnit);
        type.setActive(active);
        return type;
    }
}
