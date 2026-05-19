package com.toir.service;

import com.toir.dto.repaircampaign.RepairCampaignDto;
import com.toir.entity.Department;
import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.RepairCampaignStatus;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.service.repair.RepairCampaignService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairCampaignServiceTest {

    @Mock
    private RepairCampaignRepository repository;

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private RepairCampaignService service;

    @Test
    void findAllFilteredAppliesFiltersCorrectly() {
        UUID departmentId = UUID.randomUUID();
        Department dept = new Department();
        dept.setName("Maintenance");

        RepairCampaign c1 = new RepairCampaign();
        c1.setYear(2026);
        c1.setStatus(RepairCampaignStatus.DRAFT);
        c1.setDepartmentId(departmentId);
        c1.setStages(List.of());

        when(repository.findAllFiltered(2026, "DRAFT", "%annual%")).thenReturn(List.of(c1));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(dept));

        List<RepairCampaignDto> results = service.findAllFiltered("annual", 2026, RepairCampaignStatus.DRAFT);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).year()).isEqualTo(2026);
        assertThat(results.get(0).status()).isEqualTo(RepairCampaignStatus.DRAFT);
    }
}
