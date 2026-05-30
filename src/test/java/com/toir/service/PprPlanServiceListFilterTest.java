package com.toir.service;

import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.entity.Department;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.PlanStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.PprTaskStatus;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprPlanStatsProjection;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PprPlanServiceListFilterTest {

    @Mock
    PprPlanRepository planRepository;

    @Mock
    PprTaskRepository taskRepository;

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
    void listWithoutFiltersKeepsOldBehavior() {
        PprPlan plan = plan(2026, 5, UUID.randomUUID());
        when(planRepository.searchPlans(null, null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(plan), PageRequest.of(0, 20), 1));

        var result = service.findAll(null, null, null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(PprPlanDto::id).containsExactly(plan.getId());
        verify(planRepository).searchPlans(null, null, null, null, PageRequest.of(0, 20));
    }

    @Test
    void listWithoutPaginationReturnsAllMatchingPlans() {
        PprPlan first = plan(2026, 5, UUID.randomUUID());
        PprPlan second = plan(2026, 5, UUID.randomUUID());
        when(planRepository.searchPlans(null, null, null, null)).thenReturn(List.of(first, second));

        var result = service.findAll(null, null, null, null);

        assertThat(result).extracting(PprPlanDto::id).containsExactly(first.getId(), second.getId());
        verify(planRepository).searchPlans(null, null, null, null);
    }

    @Test
    void listFiltersByDatePartsAndDepartmentId() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.searchPlans(2026, 5, 12, departmentId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.findAll(2026, 5, 12, departmentId, 0, 20);

        verify(planRepository).searchPlans(2026, 5, 12, departmentId, PageRequest.of(0, 20));
    }

    @Test
    void listWithoutPaginationFiltersByDatePartsAndDepartmentId() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.searchPlans(2026, 5, 12, departmentId)).thenReturn(List.of());

        service.findAll(2026, 5, 12, departmentId);

        verify(planRepository).searchPlans(2026, 5, 12, departmentId);
    }

    @Test
    void listUsesRequestedPageAndSize() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.searchPlans(null, null, null, departmentId, PageRequest.of(1, 10)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 10), 0));

        service.findAll(null, null, null, departmentId, 1, 10);

        verify(planRepository).searchPlans(null, null, null, departmentId, PageRequest.of(1, 10));
    }

    @Test
    void listRejectsInvalidMonthFilter() {
        assertThatThrownBy(() -> service.findAll(2026, 13, null, null, 0, 20))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("PPR plan month filter must be between 1 and 12");
                });
    }

    @Test
    void getByIdReturnsSameVisiblePlanAsList() {
        UUID departmentId = UUID.randomUUID();
        PprPlan plan = plan(2026, 5, departmentId);

        when(planRepository.searchPlans(null, null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(plan), PageRequest.of(0, 20), 1));
        when(planRepository.findByIdAndIsDeletedFalse(plan.getId())).thenReturn(Optional.of(plan));

        var listed = service.findAll(null, null, null, null, 0, 20);
        var detailed = service.findById(plan.getId());

        assertThat(listed.getContent()).extracting(PprPlanDto::id).containsExactly(plan.getId());
        assertThat(detailed.id()).isEqualTo(plan.getId());
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).isEqualTo("PPR plan not found: " + id);
                });
    }

    @Test
    void getByIdReturnsNotFoundForSoftDeletedPlan() {
        UUID softDeletedPlanId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(softDeletedPlanId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(softDeletedPlanId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).isEqualTo("PPR plan not found: " + softDeletedPlanId);
                });
    }

    @Test
    void nonExistingDepartmentIdReturnsEmptyPageFromRepository() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.searchPlans(null, null, null, departmentId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var result = service.findAll(null, null, null, departmentId, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void responseContainsDepartmentIdAndDepartmentName() {
        UUID departmentId = UUID.randomUUID();
        PprPlan plan = plan(2026, 5, departmentId);
        Department department = new Department();
        department.setId(departmentId);
        department.setName("Mechanical");

        when(planRepository.searchPlans(null, null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(plan), PageRequest.of(0, 20), 1));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));

        var result = service.findAll(null, null, null, null, 0, 20);

        PprPlanDto dto = result.getContent().getFirst();
        assertThat(dto.departmentId()).isEqualTo(departmentId);
        assertThat(dto.departmentName()).isEqualTo("Mechanical");
    }

    @Test
    void statsWithoutFiltersReturnsCountsByStatus() {
        when(planRepository.getStats(null, null, null, null)).thenReturn(stats(6, 1, 2, 3, 8, 4, 5));

        var result = service.getStats(null, null, null, null);

        assertThat(result.totalPlans()).isEqualTo(6);
        assertThat(result.draftPlans()).isEqualTo(1);
        assertThat(result.generatedPlans()).isEqualTo(2);
        assertThat(result.approvedPlans()).isEqualTo(3);
        assertThat(result.plannedTasks()).isEqualTo(8);
        assertThat(result.inProgressTasks()).isEqualTo(4);
        assertThat(result.completedTasks()).isEqualTo(5);
        verify(planRepository).getStats(null, null, null, null);
    }

    @Test
    void getByIdIncludesEquipmentAndRegulationNames() {
        UUID equipmentId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        PprPlan plan = plan(2026, 5, UUID.randomUUID());
        PprTask task = task(plan, LocalDateTime.of(2026, 5, 1, 9, 0));
        task.setEquipmentId(equipmentId);
        task.setRegulationId(regulationId);
        plan.getTasks().add(task);
        Equipment equipment = equipment(equipmentId, "Pump 17");
        MaintenanceRegulation regulation = regulation(regulationId, "Monthly inspection");

        when(planRepository.findByIdAndIsDeletedFalse(plan.getId())).thenReturn(Optional.of(plan));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of(equipment));
        when(maintenanceRegulationRepository.findAllByIdInAndIsDeletedFalse(List.of(regulationId)))
                .thenReturn(List.of(regulation));

        var result = service.findById(plan.getId());

        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().getFirst().equipmentId()).isEqualTo(equipmentId);
        assertThat(result.tasks().getFirst().equipmentName()).isEqualTo("Pump 17");
        assertThat(result.tasks().getFirst().regulationId()).isEqualTo(regulationId);
        assertThat(result.tasks().getFirst().regulationName()).isEqualTo("Monthly inspection");
    }

    @Test
    void listIncludesEquipmentAndRegulationNamesForEveryItem() {
        UUID firstEquipmentId = UUID.randomUUID();
        UUID secondEquipmentId = UUID.randomUUID();
        UUID firstRegulationId = UUID.randomUUID();
        UUID secondRegulationId = UUID.randomUUID();
        PprPlan firstPlan = plan(2026, 5, UUID.randomUUID());
        PprPlan secondPlan = plan(2026, 5, UUID.randomUUID());
        PprTask firstTask = task(firstPlan, LocalDateTime.of(2026, 5, 1, 9, 0));
        firstTask.setEquipmentId(firstEquipmentId);
        firstTask.setRegulationId(firstRegulationId);
        firstPlan.getTasks().add(firstTask);
        PprTask secondTask = task(secondPlan, LocalDateTime.of(2026, 5, 2, 9, 0));
        secondTask.setEquipmentId(secondEquipmentId);
        secondTask.setRegulationId(secondRegulationId);
        secondPlan.getTasks().add(secondTask);

        when(planRepository.searchPlans(null, null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(firstPlan, secondPlan), PageRequest.of(0, 20), 2));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(firstEquipmentId, secondEquipmentId)))
                .thenReturn(List.of(equipment(firstEquipmentId, "Compressor A"), equipment(secondEquipmentId, "Pump B")));
        when(maintenanceRegulationRepository.findAllByIdInAndIsDeletedFalse(List.of(firstRegulationId, secondRegulationId)))
                .thenReturn(List.of(regulation(firstRegulationId, "Quarterly PM"), regulation(secondRegulationId, "Annual PM")));

        var result = service.findAll(null, null, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).tasks().getFirst().equipmentName()).isEqualTo("Compressor A");
        assertThat(result.getContent().get(0).tasks().getFirst().regulationName()).isEqualTo("Quarterly PM");
        assertThat(result.getContent().get(1).tasks().getFirst().equipmentName()).isEqualTo("Pump B");
        assertThat(result.getContent().get(1).tasks().getFirst().regulationName()).isEqualTo("Annual PM");
    }

    @Test
    void getByIdLeavesNamesNullWhenOptionalRelationsAreMissing() {
        PprPlan plan = plan(2026, 5, UUID.randomUUID());
        PprTask task = task(plan, LocalDateTime.of(2026, 5, 1, 9, 0));
        task.setEquipmentId(null);
        task.setRegulationId(null);
        plan.getTasks().add(task);

        when(planRepository.findByIdAndIsDeletedFalse(plan.getId())).thenReturn(Optional.of(plan));

        var result = service.findById(plan.getId());

        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().getFirst().equipmentId()).isNull();
        assertThat(result.tasks().getFirst().equipmentName()).isNull();
        assertThat(result.tasks().getFirst().regulationId()).isNull();
        assertThat(result.tasks().getFirst().regulationName()).isNull();
    }

    @Test
    void statsWithDepartmentUsesSameFilter() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.getStats(2026, 5, 12, departmentId)).thenReturn(stats(2, 0, 1, 1, 3, 2, 1));

        var result = service.getStats(2026, 5, 12, departmentId);

        assertThat(result.totalPlans()).isEqualTo(2);
        assertThat(result.generatedPlans()).isEqualTo(1);
        assertThat(result.approvedPlans()).isEqualTo(1);
        verify(planRepository).getStats(2026, 5, 12, departmentId);
    }

    @Test
    void statsForNonExistingDepartmentReturnsZeroCounts() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.getStats(null, null, null, departmentId)).thenReturn(stats(0, 0, 0, 0, 0, 0, 0));

        var result = service.getStats(null, null, null, departmentId);

        assertThat(result.totalPlans()).isZero();
        assertThat(result.plannedTasks()).isZero();
        verify(planRepository).getStats(null, null, null, departmentId);
    }

    @Test
    void findTasksByPlanUsesStableScheduleOrder() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(2026, 5, UUID.randomUUID());
        plan.setId(planId);
        PprTask first = task(plan, LocalDateTime.of(2026, 5, 1, 9, 0));
        PprTask second = task(plan, LocalDateTime.of(2026, 5, 2, 9, 0));
        when(taskRepository.findAllByPlanIdAndIsDeletedFalseOrderByScheduledStartAscIdAsc(planId))
                .thenReturn(List.of(first, second));

        var result = service.findTasksByPlan(planId);

        assertThat(result).extracting("id").containsExactly(first.getId(), second.getId());
        verify(taskRepository).findAllByPlanIdAndIsDeletedFalseOrderByScheduledStartAscIdAsc(planId);
    }

    private PprPlan plan(int year, int month, UUID departmentId) {
        PprPlan plan = new PprPlan();
        plan.setId(UUID.randomUUID());
        plan.setCode("PPR-2026-0001");
        plan.setName("May plan");
        plan.setStartDate(LocalDate.of(year, month, 1));
        plan.setEndDate(LocalDate.of(year, month, 1).plusMonths(1).minusDays(1));
        plan.setStatus(PlanStatus.DRAFT);
        plan.setDepartmentId(departmentId);
        plan.setCreatedById(UUID.randomUUID());
        plan.setTasks(new ArrayList<>());
        return plan;
    }

    private PprTask task(PprPlan plan, LocalDateTime scheduledStart) {
        PprTask task = new PprTask();
        task.setId(UUID.randomUUID());
        task.setCode("PPR-TASK-" + scheduledStart.toLocalDate());
        task.setPlan(plan);
        task.setRegulationId(null);
        task.setEquipmentId(null);
        task.setTitle("Task");
        task.setScheduledStart(scheduledStart);
        task.setScheduledEnd(scheduledStart.plusHours(2));
        task.setDueDate(scheduledStart.plusDays(1));
        task.setStatus(PprTaskStatus.PLANNED);
        task.setPriority(PriorityLevel.MEDIUM);
        task.setPlannedLaborHours(2.0);
        return task;
    }

    private Equipment equipment(UUID id, String name) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setName(name);
        return equipment;
    }

    private MaintenanceRegulation regulation(UUID id, String name) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(id);
        regulation.setName(name);
        return regulation;
    }

    private PprPlanStatsProjection stats(
            long totalPlans,
            long draftPlans,
            long generatedPlans,
            long approvedPlans,
            long plannedTasks,
            long inProgressTasks,
            long completedTasks
    ) {
        return new PprPlanStatsProjection() {
            @Override
            public Long getTotalPlans() {
                return totalPlans;
            }

            @Override
            public Long getDraftPlans() {
                return draftPlans;
            }

            @Override
            public Long getGeneratedPlans() {
                return generatedPlans;
            }

            @Override
            public Long getApprovedPlans() {
                return approvedPlans;
            }

            @Override
            public Long getPlannedTasks() {
                return plannedTasks;
            }

            @Override
            public Long getInProgressTasks() {
                return inProgressTasks;
            }

            @Override
            public Long getCompletedTasks() {
                return completedTasks;
            }
        };
    }
}
