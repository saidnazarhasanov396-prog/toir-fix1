package com.toir.service;

import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    PprTaskRepository pprTaskRepository;

    @Mock
    DowntimeEventRepository downtimeEventRepository;

    @Mock
    ReliabilityMetricRepository reliabilityMetricRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @InjectMocks
    AnalyticsService service;

    @Test
    void unknownEquipmentReturnsNotFound() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.equipmentAnalytics(equipmentId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment not found");
    }

    @Test
    void existingEquipmentWithNoDowntimeReturnsStableEmptyArrays() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(reliabilityMetricRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(eq(equipmentId)))
                .thenReturn(List.of());
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(eq(equipmentId)))
                .thenReturn(List.of());

        EquipmentAnalyticsResponse response = service.equipmentAnalytics(equipmentId);

        assertThat(response.equipmentId()).isEqualTo(equipmentId.toString());
        assertThat(response.history()).isEmpty();
        assertThat(response.downtimes()).isEmpty();
        assertThat(response.events()).isEmpty();
        assertThat(response.downtimeMinutes()).isZero();
    }

    @Test
    void nullRepositoryListsAreNormalizedToEmptyArrays() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(reliabilityMetricRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(eq(equipmentId)))
                .thenReturn(null);
        when(downtimeEventRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(eq(equipmentId)))
                .thenReturn(null);

        EquipmentAnalyticsResponse response = service.equipmentAnalytics(equipmentId);

        assertThat(response.history()).isNotNull().isEmpty();
        assertThat(response.downtimes()).isNotNull().isEmpty();
        assertThat(response.events()).isNotNull().isEmpty();
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-0009");
        equipment.setName("Line Motor");
        equipment.setInventoryNumber("INV-300");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }
}

