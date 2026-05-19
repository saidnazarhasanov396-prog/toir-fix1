package com.toir.service;

import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.entity.PlannedShutdown;
import com.toir.enums.PlanStatus;
import com.toir.repository.PlannedShutdownRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannedShutdownServiceTest {

    @Mock
    private PlannedShutdownRepository repository;

    @InjectMocks
    private PlannedShutdownService service;

    @Test
    void findAllFilteredAppliesFiltersCorrectly() {
        UUID departmentId = UUID.randomUUID();

        PlannedShutdown s1 = new PlannedShutdown();
        s1.setId(UUID.randomUUID());
        s1.setName("Annual Maintenance");
        s1.setDepartmentId(departmentId);
        s1.setStartAt(Instant.parse("2026-05-19T10:00:00Z"));
        s1.setEndAt(Instant.parse("2026-05-19T18:00:00Z"));
        s1.setReason("Routine check");
        s1.setStatus(PlanStatus.DRAFT);

        when(repository.findAllFiltered(departmentId, "DRAFT", "%annual%")).thenReturn(List.of(s1));

        List<PlannedShutdownDto> results = service.findAllFiltered(departmentId, PlanStatus.DRAFT, "annual");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Annual Maintenance");
        assertThat(results.get(0).status()).isEqualTo(PlanStatus.DRAFT);
    }
}
