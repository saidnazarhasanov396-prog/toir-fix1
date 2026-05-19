package com.toir.service;

import com.toir.dto.maintenancetemplate.MaintenanceTemplateStatsResponse;
import com.toir.enums.MaintenanceKind;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.repository.maintenance.MaintenanceTemplateStatsProjection;
import com.toir.service.maintanance.MaintenanceTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceTemplateServiceStatsTest {

    @Mock
    private MaintenanceTemplateRepository repository;

    @Mock
    private SparePartService sparePartService;

    @InjectMocks
    private MaintenanceTemplateService service;

    @Test
    void getStatsReturnsCorrectAggregates() {
        MaintenanceTemplateStatsProjection projection = mock(MaintenanceTemplateStatsProjection.class);
        when(projection.getTotalTemplates()).thenReturn(10L);
        when(projection.getWithOperations()).thenReturn(7L);
        when(projection.getTotalOperations()).thenReturn(25L);
        when(projection.getAvgOperationsPerTemplate()).thenReturn(2.5f);

        when(sparePartService.toSearchPattern("pump")).thenReturn("%pump%");
        when(repository.getTemplateStats("%pump%", MaintenanceKind.PREVENTIVE.name()))
                .thenReturn(projection);

        MaintenanceTemplateStatsResponse stats = service.getStats("pump", MaintenanceKind.PREVENTIVE);

        assertThat(stats.totalTemplates()).isEqualTo(10L);
        assertThat(stats.withOperations()).isEqualTo(7L);
        assertThat(stats.totalOperations()).isEqualTo(25L);
        assertThat(stats.avgPerTemplate()).isEqualTo(2.5f);
    }

    @Test
    void getStatsHandlesNullOrEmptyStatsGracefully() {
        when(repository.getTemplateStats(null, null)).thenReturn(null);

        MaintenanceTemplateStatsResponse stats = service.getStats(null, null);

        assertThat(stats.totalTemplates()).isEqualTo(0L);
        assertThat(stats.withOperations()).isEqualTo(0L);
        assertThat(stats.totalOperations()).isEqualTo(0L);
        assertThat(stats.avgPerTemplate()).isEqualTo(0.0f);
    }
}
