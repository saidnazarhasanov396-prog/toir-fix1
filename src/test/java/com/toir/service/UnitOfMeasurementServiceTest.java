package com.toir.service;

import com.toir.entity.UnitOfMeasurement;
import com.toir.exception.RestException;
import com.toir.repository.UnitOfMeasurementRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnitOfMeasurementServiceTest {

    @Mock
    UnitOfMeasurementRepository repository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    UnitOfMeasurementService service;

    @Test
    void normalizeRequiredUnitByIdReturnsCanonicalName() {
        UUID unitId = UUID.randomUUID();
        UnitOfMeasurement uom = new UnitOfMeasurement();
        uom.setId(unitId);
        uom.setCode("UOM-2026-0001");
        uom.setName("bar");
        when(repository.findByIdAndIsDeletedFalse(unitId)).thenReturn(Optional.of(uom));

        String normalized = service.normalizeRequiredUnitOrThrow(unitId.toString(), "condition reading unit");

        assertThat(normalized).isEqualTo("bar");
    }

    @Test
    void normalizeRequiredUnknownUnitIdThrowsBadRequest() {
        UUID unitId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(unitId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.normalizeRequiredUnitOrThrow(unitId.toString(), "condition reading unit"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Unknown condition reading unit")
                .hasMessageContaining(unitId.toString());
    }

    @Test
    void normalizeOptionalUnitByCodeReturnsCanonicalName() {
        UnitOfMeasurement uom = new UnitOfMeasurement();
        uom.setCode("UOM-2026-0001");
        uom.setName("bar");
        when(repository.findByTokenIgnoreCase("UOM-2026-0001")).thenReturn(List.of(uom));

        String normalized = service.normalizeOptionalUnitOrNull(" UOM-2026-0001 ");

        assertThat(normalized).isEqualTo("bar");
    }

    @Test
    void normalizeOptionalUnitByNameReturnsCanonicalName() {
        UnitOfMeasurement uom = new UnitOfMeasurement();
        uom.setCode("UOM-2026-0002");
        uom.setName("mm");
        when(repository.findByTokenIgnoreCase("mm")).thenReturn(List.of(uom));

        String normalized = service.normalizeOptionalUnitOrNull("mm");

        assertThat(normalized).isEqualTo("mm");
    }

    @Test
    void normalizeOptionalBlankUnitReturnsNull() {
        String normalized = service.normalizeOptionalUnitOrNull("   ");

        assertThat(normalized).isNull();
    }

    @Test
    void normalizeRequiredUnknownUnitThrowsBadRequest() {
        when(repository.findByTokenIgnoreCase("mystery")).thenReturn(List.of());

        assertThatThrownBy(() -> service.normalizeRequiredUnitOrThrow("mystery", "condition reading unit"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Unknown condition reading unit");
    }
}
