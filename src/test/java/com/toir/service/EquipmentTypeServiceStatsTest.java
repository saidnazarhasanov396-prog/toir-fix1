package com.toir.service;

import com.toir.dto.equipmenttype.EquipmentTypeStatsResponse;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.equipment.EquipmentTypeStatsProjection;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentTypeServiceStatsTest {

    @Mock
    EquipmentTypeRepository repository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    AuditSerializationService auditSerializationService;

    @InjectMocks
    com.toir.service.equipment.EquipmentTypeService service;

    @Test
    void getStatsWithNoFiltersReturnsMappedResponse() {
        EquipmentTypeStatsProjection projection = mockProjection(15L, 3L, 10L, 2L);
        when(repository.getEquipmentTypeStats(isNull(), isNull())).thenReturn(projection);

        EquipmentTypeStatsResponse stats = service.getStats(null, null);

        assertThat(stats.totalTypes()).isEqualTo(15);
        assertThat(stats.activeCategories()).isEqualTo(3);
        assertThat(stats.withActiveEquipment()).isEqualTo(10);
        assertThat(stats.recentlyAdded()).isEqualTo(2);
        verify(repository).getEquipmentTypeStats(null, null);
    }

    @Test
    void getStatsWithSearchPassesLikePatternsToRepository() {
        EquipmentTypeStatsProjection projection = mockProjection(5L, 1L, 3L, 1L);
        when(repository.getEquipmentTypeStats(isNull(), org.mockito.ArgumentMatchers.eq("%pump%"))).thenReturn(projection);

        service.getStats("pump", null);

        verify(repository).getEquipmentTypeStats(null, "%pump%");
    }

    @Test
    void getStatsWithCategoryFilterPassesCategoryToRepository() {
        EquipmentTypeStatsProjection projection = mockProjection(4L, 1L, 3L, 0L);
        when(repository.getEquipmentTypeStats(org.mockito.ArgumentMatchers.eq("ROTATING"), isNull())).thenReturn(projection);

        service.getStats(null, "ROTATING");

        verify(repository).getEquipmentTypeStats("ROTATING", null);
    }

    @Test
    void getStatsHandlesNullProjectionValuesGracefully() {
        EquipmentTypeStatsProjection projection = mockProjection(null, null, null, null);
        when(repository.getEquipmentTypeStats(isNull(), isNull())).thenReturn(projection);

        EquipmentTypeStatsResponse stats = service.getStats(null, null);

        assertThat(stats.totalTypes()).isZero();
        assertThat(stats.activeCategories()).isZero();
        assertThat(stats.withActiveEquipment()).isZero();
        assertThat(stats.recentlyAdded()).isZero();
    }

    private EquipmentTypeStatsProjection mockProjection(Long total, Long categories, Long withEquip, Long recent) {
        EquipmentTypeStatsProjection p = mock(EquipmentTypeStatsProjection.class);
        when(p.getTotalTypes()).thenReturn(total);
        when(p.getActiveCategories()).thenReturn(categories);
        when(p.getWithActiveEquipment()).thenReturn(withEquip);
        when(p.getRecentlyAdded()).thenReturn(recent);
        return p;
    }
}
