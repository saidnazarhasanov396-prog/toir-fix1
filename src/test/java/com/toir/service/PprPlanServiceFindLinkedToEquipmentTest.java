package com.toir.service;

import com.toir.dto.pprplanning.EquipmentLinkedPprPlanDto;
import com.toir.entity.PprPlan;
import com.toir.entity.PprPlanTarget;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.PprTargetType;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PprPlanServiceFindLinkedToEquipmentTest {

    @Mock
    PprPlanRepository planRepository;
    @Mock
    PprTaskRepository taskRepository;
    @Mock
    PprTaskQueryService pprTaskQueryService;
    @Mock
    DepartmentRepository departmentRepository;
    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    EquipmentTypeRepository equipmentTypeRepository;
    @Mock
    EquipmentMaintenanceRuleRepository equipmentMaintenanceRuleRepository;
    @Mock
    MaintenanceRegulationRepository maintenanceRegulationRepository;
    @Mock
    AuditBuilderService auditBuilderService;
    @Mock
    AuditSerializationService auditSerializationService;

    @InjectMocks
    PprPlanService service;

    @Test
    void findLinkedToEquipmentThrowsWhenEquipmentMissing() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findLinkedToEquipment(equipmentId, false))
                .isInstanceOf(RestException.class)
                .extracting(ex -> ((RestException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findLinkedToEquipmentReturnsPlansWithLinkReasons() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setEquipmentTypeId(typeId);

        PprPlan plan = plan(equipmentId, typeId);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(planRepository.findLinkedToEquipment(equipmentId, typeId)).thenReturn(List.of(plan));
        when(taskRepository.countByPlanIdAndIsDeletedFalse(plan.getId())).thenReturn(1L);

        var response = service.findLinkedToEquipment(equipmentId, false);

        assertThat(response.equipmentId()).isEqualTo(equipmentId);
        assertThat(response.equipmentTypeId()).isEqualTo(typeId);
        assertThat(response.planCount()).isEqualTo(1);
        assertThat(response.plans()).hasSize(1);
        assertThat(response.plans().getFirst().plan().id()).isEqualTo(plan.getId());
        assertThat(response.plans().getFirst().linkReasons()).containsExactly(
                EquipmentLinkedPprPlanDto.REASON_EQUIPMENT_TARGET,
                EquipmentLinkedPprPlanDto.REASON_EQUIPMENT_TYPE_TARGET,
                EquipmentLinkedPprPlanDto.REASON_TASK
        );
        assertThat(response.plans().getFirst().plan().tasks()).isEmpty();
        assertThat(response.plans().getFirst().plan().taskCount()).isEqualTo(1L);
    }

    private PprPlan plan(UUID equipmentId, UUID typeId) {
        PprPlan plan = new PprPlan();
        plan.setId(UUID.randomUUID());
        plan.setCode("PPR-TEST-1");
        plan.setName("Linked plan");
        plan.setStartDate(LocalDate.of(2026, 1, 1));
        plan.setEndDate(LocalDate.of(2026, 12, 31));
        plan.setTargets(new ArrayList<>());
        plan.setTasks(new ArrayList<>());

        PprPlanTarget equipmentTarget = new PprPlanTarget();
        equipmentTarget.setTargetType(PprTargetType.EQUIPMENT);
        equipmentTarget.setEquipmentId(equipmentId);
        plan.getTargets().add(equipmentTarget);

        PprPlanTarget typeTarget = new PprPlanTarget();
        typeTarget.setTargetType(PprTargetType.EQUIPMENT_TYPE);
        typeTarget.setEquipmentTypeId(typeId);
        plan.getTargets().add(typeTarget);

        PprTask task = new PprTask();
        task.setId(UUID.randomUUID());
        task.setEquipmentId(equipmentId);
        plan.getTasks().add(task);
        return plan;
    }
}
