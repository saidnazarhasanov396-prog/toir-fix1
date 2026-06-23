package com.toir.service;

import com.toir.controller.ParetoController.ParetoItem;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.RequestStatus;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParetoServiceTest {

    @Mock
    DefectRepository defectRepository;

    @Mock
    DowntimeEventRepository downtimeRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    ParetoService service;

    @BeforeEach
    void setUpScope() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
    }

    @Test
    void downtimeCausesUsesRepairRequestsWhenExplicitDowntimeEventsAreMissing() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setDepartmentId(departmentId);
        RepairRequest completedFailure = RepairRequest.builder()
                .equipmentId(equipmentId)
                .departmentId(departmentId)
                .status(RequestStatus.CLOSED)
                .detectedAt(now.minus(Duration.ofHours(10)))
                .actualCompletionAt(now.minus(Duration.ofHours(5)))
                .build();

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(downtimeRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(workOrderRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(completedFailure));

        Page<ParetoItem> page = service.downtimeCauses(null, null, 0, 10);

        assertThat(page.getContent()).singleElement().satisfies(item -> {
            assertThat(item.key()).isEqualTo("REPAIR_REQUEST");
            assertThat(item.value()).isEqualTo(300.0);
            assertThat(item.cumulativePct()).isEqualTo(100.0);
        });
    }
}
