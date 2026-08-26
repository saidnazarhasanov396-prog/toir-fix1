package com.toir.service;

import com.toir.dto.pprplanning.EquipmentLinkedPprPlanDto;
import com.toir.entity.PprPlan;
import com.toir.entity.PprPlanTarget;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PprTargetType;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.service.maintanance.EquipmentMaintenanceEffectiveRule;
import com.toir.service.maintanance.EquipmentMaintenanceEffectiveRuleResolver;
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
    @Mock
    EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;

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
    void findDirectPlannedWorksReturnsApplicableRegulationsForEquipment() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setEquipmentTypeId(typeId);

        MaintenanceRegulation oil = regulation(UUID.randomUUID(), "Oil change", MaintenanceKind.PREVENTIVE);
        oil.setPeriodicityUnit(PeriodicityUnit.MONTH);
        oil.setPeriodicityValue(2);
        MaintenanceRegulation bolts = regulation(UUID.randomUUID(), "Bolt replacement", MaintenanceKind.PREVENTIVE);
        bolts.setPeriodicityUnit(PeriodicityUnit.WEEK);
        bolts.setPeriodicityValue(1);
        MaintenanceRegulation motor = regulation(UUID.randomUUID(), "Motor repair", MaintenanceKind.CURRENT_REPAIR);
        motor.setPeriodicityUnit(PeriodicityUnit.MONTH);
        motor.setPeriodicityValue(6);
        MaintenanceRegulation general = regulation(UUID.randomUUID(), "General inspection", MaintenanceKind.INSPECTION);
        general.setPeriodicityUnit(PeriodicityUnit.MONTH);
        general.setPeriodicityValue(3);

        EquipmentMaintenanceRule individual = new EquipmentMaintenanceRule();
        individual.setId(UUID.randomUUID());
        individual.setEquipmentId(equipmentId);
        individual.setName("Seasonal service");
        individual.setCode("SEASONAL-SERVICE");
        individual.setMaintenanceKind(MaintenanceKind.SEASONAL);
        individual.setPeriodicityUnit(PeriodicityUnit.YEAR);
        individual.setPeriodicityValue(1);
        individual.setActive(true);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, oil),
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, bolts),
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, motor),
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, general),
                EquipmentMaintenanceEffectiveRule.fromIndividual(individual)
        ));

        var response = service.findDirectPlannedWorks(equipmentId);

        assertThat(response.equipmentId()).isEqualTo(equipmentId);
        assertThat(response.equipmentTypeId()).isEqualTo(typeId);
        assertThat(response.plannedWorkCount()).isEqualTo(5);
        assertThat(response.plannedWorks())
                .extracting(work -> work.title())
                .containsExactly(
                        "Oil change",
                        "Bolt replacement",
                        "Motor repair",
                        "General inspection",
                        "Seasonal service"
                );
        assertThat(response.plannedWorks())
                .anySatisfy(work -> {
                    assertThat(work.title()).isEqualTo("Oil change");
                    assertThat(work.regulationId()).isEqualTo(oil.getId());
                    assertThat(work.periodicityUnit()).isEqualTo(PeriodicityUnit.MONTH);
                    assertThat(work.periodicityValue()).isEqualTo(2);
                    assertThat(work.workKey()).isEqualTo("REGULATION:" + oil.getId());
                });
        assertThat(response.plannedWorks())
                .anySatisfy(work -> {
                    assertThat(work.title()).isEqualTo("Seasonal service");
                    assertThat(work.equipmentMaintenanceRuleId()).isEqualTo(individual.getId());
                    assertThat(work.periodicityUnit()).isEqualTo(PeriodicityUnit.YEAR);
                    assertThat(work.periodicityValue()).isEqualTo(1);
                    assertThat(work.workKey()).isEqualTo("RULE:" + individual.getId());
                });
    }

    @Test
    void findDirectPlannedWorksReturnsEmptyWhenEquipmentHasNoApplicableRules() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setEquipmentTypeId(typeId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of());

        var response = service.findDirectPlannedWorks(equipmentId);

        assertThat(response.plannedWorkCount()).isEqualTo(0);
        assertThat(response.plannedWorks()).isEmpty();
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
