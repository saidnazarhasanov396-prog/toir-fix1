package com.toir.service;

import com.toir.dto.pprplanning.EquipmentLinkedPprPlanDto;
import com.toir.entity.PprPlan;
import com.toir.entity.PprPlanTarget;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceKind;
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
import java.time.LocalDateTime;
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
        assertThat(response.plans().getFirst().plannedWorks()).hasSize(1);
    }

    @Test
    void findLinkedToEquipmentGroupsYearlyTasksIntoUniquePlannedWorks() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        UUID oilChangeId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setEquipmentTypeId(typeId);

        PprPlan plan = plan(equipmentId, typeId);
        plan.getTasks().clear();
        plan.getTasks().add(task(equipmentId, inspectionId, "Motor inspection",
                java.time.LocalDateTime.of(2026, 3, 1, 9, 0)));
        plan.getTasks().add(task(equipmentId, inspectionId, "Motor inspection",
                java.time.LocalDateTime.of(2026, 6, 1, 9, 0)));
        plan.getTasks().add(task(equipmentId, inspectionId, "Motor inspection",
                java.time.LocalDateTime.of(2026, 9, 1, 9, 0)));
        plan.getTasks().add(task(equipmentId, oilChangeId, "Oil change",
                java.time.LocalDateTime.of(2026, 1, 15, 8, 0)));
        plan.getTasks().add(task(equipmentId, oilChangeId, "Oil change",
                java.time.LocalDateTime.of(2026, 4, 15, 8, 0)));

        MaintenanceRegulation inspection = regulation(inspectionId, "Motor inspection", MaintenanceKind.INSPECTION);
        MaintenanceRegulation oil = regulation(oilChangeId, "Oil change", MaintenanceKind.CURRENT_REPAIR);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(planRepository.findLinkedToEquipment(equipmentId, typeId)).thenReturn(List.of(plan));
        when(taskRepository.countByPlanIdAndIsDeletedFalse(plan.getId())).thenReturn(5L);
        when(maintenanceRegulationRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(inspection, oil));

        var response = service.findLinkedToEquipment(equipmentId, false);

        assertThat(response.plans().getFirst().plan().tasks()).isEmpty();
        assertThat(response.plans().getFirst().plannedWorks()).hasSize(2);
        assertThat(response.plans().getFirst().plannedWorks())
                .extracting(work -> work.title())
                .containsExactlyInAnyOrder("Motor inspection", "Oil change");
        assertThat(response.plans().getFirst().plannedWorks())
                .extracting(work -> work.occurrenceCount())
                .containsExactlyInAnyOrder(3, 2);
        assertThat(response.plans().getFirst().plannedWorks())
                .anySatisfy(work -> {
                    assertThat(work.title()).isEqualTo("Oil change");
                    assertThat(work.maintenanceKind()).isEqualTo(MaintenanceKind.CURRENT_REPAIR);
                    assertThat(work.firstTaskStartsAt()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 15, 8, 0));
                });
        assertThat(response.plans().getFirst().plannedWorks())
                .anySatisfy(work -> {
                    assertThat(work.title()).isEqualTo("Motor inspection");
                    assertThat(work.maintenanceKind()).isEqualTo(MaintenanceKind.INSPECTION);
                    assertThat(work.firstTaskStartsAt()).isEqualTo(java.time.LocalDateTime.of(2026, 3, 1, 9, 0));
                });
    }


    @Test
    void findDirectPlannedWorksReturnsOnlyWorksWithoutPlans() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setEquipmentTypeId(typeId);

        PprPlan direct = plan(equipmentId, typeId);
        direct.getTasks().clear();
        PprTask task = task(equipmentId, regulationId, "Oil change", LocalDateTime.of(2026, 3, 1, 9, 0));
        direct.getTasks().add(task);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(planRepository.findDirectlyLinkedToEquipment(equipmentId)).thenReturn(List.of(direct));
        when(maintenanceRegulationRepository.findAllByIdInAndIsDeletedFalse(List.of(regulationId)))
                .thenReturn(List.of(regulation(regulationId, "Oil change", MaintenanceKind.PREVENTIVE)));

        var response = service.findDirectPlannedWorks(equipmentId);

        assertThat(response.equipmentId()).isEqualTo(equipmentId);
        assertThat(response.plannedWorkCount()).isEqualTo(1);
        assertThat(response.plannedWorks()).hasSize(1);
        assertThat(response.plannedWorks().getFirst().title()).isEqualTo("Oil change");
        assertThat(response.plannedWorks().getFirst().maintenanceKind()).isEqualTo(MaintenanceKind.PREVENTIVE);
    }

    @Test
    void findDirectPlannedWorksReturnsOneLatestWorkPerUniqueType() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID oilChange2025Id = UUID.randomUUID();
        UUID oilChange2026Id = UUID.randomUUID();
        UUID motorRepairId = UUID.randomUUID();
        UUID generalId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setEquipmentTypeId(typeId);
        equipment.setCode("PUMP-1");

        PprPlan olderPlan = plan(equipmentId, typeId);
        olderPlan.getTasks().clear();
        olderPlan.getTasks().add(task(equipmentId, oilChange2025Id, "Oil change - PUMP-1",
                LocalDateTime.of(2025, 1, 15, 8, 0)));
        olderPlan.getTasks().add(task(equipmentId, oilChange2025Id, "Oil change - PUMP-1",
                LocalDateTime.of(2025, 7, 15, 8, 0)));
        olderPlan.getTasks().add(task(equipmentId, motorRepairId, "Motor repair",
                LocalDateTime.of(2025, 4, 1, 9, 0)));

        PprPlan newerPlan = plan(equipmentId, typeId);
        newerPlan.getTasks().clear();
        newerPlan.getTasks().add(task(equipmentId, oilChange2026Id, "Oil change",
                LocalDateTime.of(2026, 3, 1, 8, 0)));
        newerPlan.getTasks().add(task(equipmentId, generalId, "General",
                LocalDateTime.of(2026, 6, 1, 9, 0)));

        MaintenanceRegulation oil2025 = regulation(oilChange2025Id, "Oil change", MaintenanceKind.PREVENTIVE);
        MaintenanceRegulation oil2026 = regulation(oilChange2026Id, "Oil change", MaintenanceKind.PREVENTIVE);
        MaintenanceRegulation motor = regulation(motorRepairId, "Motor repair", MaintenanceKind.CURRENT_REPAIR);
        MaintenanceRegulation general = regulation(generalId, "General", MaintenanceKind.OVERHAUL);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(planRepository.findDirectlyLinkedToEquipment(equipmentId)).thenReturn(List.of(newerPlan, olderPlan));
        when(maintenanceRegulationRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(oil2025, oil2026, motor, general));

        var response = service.findDirectPlannedWorks(equipmentId);

        assertThat(response.plannedWorkCount()).isEqualTo(3);
        assertThat(response.plannedWorks())
                .extracting(work -> work.title())
                .containsExactlyInAnyOrder("Oil change", "Motor repair", "General");
        assertThat(response.plannedWorks())
                .anySatisfy(work -> {
                    assertThat(work.title()).isEqualTo("Oil change");
                    assertThat(work.occurrenceCount()).isEqualTo(3);
                    assertThat(work.regulationId()).isEqualTo(oilChange2026Id);
                    assertThat(work.firstTaskStartsAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 8, 0));
                });
        assertThat(response.plannedWorks())
                .anySatisfy(work -> {
                    assertThat(work.title()).isEqualTo("Motor repair");
                    assertThat(work.maintenanceKind()).isEqualTo(MaintenanceKind.CURRENT_REPAIR);
                    assertThat(work.occurrenceCount()).isEqualTo(1);
                });
        assertThat(response.plannedWorks())
                .anySatisfy(work -> {
                    assertThat(work.title()).isEqualTo("General");
                    assertThat(work.maintenanceKind()).isEqualTo(MaintenanceKind.OVERHAUL);
                });
    }

    @Test
    void findDirectPlannedWorksMergesRuleAndRegulationWithSameWorkTypeName() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setEquipmentTypeId(typeId);
        equipment.setCode("EQ-9");

        PprPlan plan = plan(equipmentId, typeId);
        plan.getTasks().clear();
        PprTask regulationTask = task(equipmentId, regulationId, "Oil change",
                LocalDateTime.of(2026, 1, 10, 8, 0));
        PprTask ruleTask = task(equipmentId, null, "Oil change - EQ-9",
                LocalDateTime.of(2026, 8, 10, 8, 0));
        ruleTask.setEquipmentMaintenanceRuleId(ruleId);
        plan.getTasks().add(regulationTask);
        plan.getTasks().add(ruleTask);

        EquipmentMaintenanceRule rule = new EquipmentMaintenanceRule();
        rule.setId(ruleId);
        rule.setName("Oil change");
        rule.setCode("OIL-CHANGE");
        rule.setMaintenanceKind(MaintenanceKind.PREVENTIVE);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(planRepository.findDirectlyLinkedToEquipment(equipmentId)).thenReturn(List.of(plan));
        when(maintenanceRegulationRepository.findAllByIdInAndIsDeletedFalse(List.of(regulationId)))
                .thenReturn(List.of(regulation(regulationId, "Oil change", MaintenanceKind.PREVENTIVE)));
        when(equipmentMaintenanceRuleRepository.findAllByIdInAndIsDeletedFalse(List.of(ruleId)))
                .thenReturn(List.of(rule));

        var response = service.findDirectPlannedWorks(equipmentId);

        assertThat(response.plannedWorkCount()).isEqualTo(1);
        assertThat(response.plannedWorks()).hasSize(1);
        assertThat(response.plannedWorks().getFirst().title()).isEqualTo("Oil change");
        assertThat(response.plannedWorks().getFirst().occurrenceCount()).isEqualTo(2);
        assertThat(response.plannedWorks().getFirst().firstTaskStartsAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 10, 8, 0));
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
        task.setTitle("Linked task");
        plan.getTasks().add(task);
        return plan;
    }

    private PprTask task(UUID equipmentId, UUID regulationId, String title) {
        return task(equipmentId, regulationId, title, null);
    }

    private PprTask task(UUID equipmentId, UUID regulationId, String title, java.time.LocalDateTime startsAt) {
        PprTask task = new PprTask();
        task.setId(UUID.randomUUID());
        task.setEquipmentId(equipmentId);
        task.setRegulationId(regulationId);
        task.setTitle(title);
        task.setScheduledStart(startsAt);
        return task;
    }

    private MaintenanceRegulation regulation(UUID id, String name, MaintenanceKind kind) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(id);
        regulation.setName(name);
        regulation.setCode(name.toUpperCase().replace(' ', '_'));
        regulation.setMaintenanceKind(kind);
        return regulation;
    }
}
