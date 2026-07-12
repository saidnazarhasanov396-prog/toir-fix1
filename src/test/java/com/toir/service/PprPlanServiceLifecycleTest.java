package com.toir.service;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.dto.pprplanning.CompletePprTaskRequest;
import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.dto.pprplanning.PprPlanRequest;
import com.toir.dto.pprplanning.PprTaskRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprPlanTarget;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprFrequency;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprTargetType;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PprType;
import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.service.repair.RepairMaterialUsageService;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PprPlanServiceLifecycleTest {

    @Mock
    PprPlanRepository planRepository;

    @Mock
    PprTaskRepository taskRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

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
    PprGeneratorService generatorService;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    AuditSerializationService auditSerializationService;

    @Mock
    RepairMaterialUsageService repairMaterialUsageService;

    @Mock
    EntityManager entityManager;

    @InjectMocks
    PprPlanService service;

    @BeforeEach
    void setUp() {
        lenient().when(generatorService.generateForPlan(any(UUID.class)))
                .thenAnswer(invocation -> new PprGeneratorService.GenerationResult(invocation.getArgument(0), 0, 0));
    }

    @Test
    void createPlanWithDateRangeDerivesLegacyYearMonth() {
        UUID createdById = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        String codePrefix = "PPR-" + Year.now().getValue() + "-";
        String expectedCode = "PPR-" + Year.now().getValue() + "-0001";
        when(planRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(planRepository.existsByCodeAndIsDeletedFalse(expectedCode)).thenReturn(false);
        when(planRepository.saveAndFlush(any(PprPlan.class))).thenAnswer(invocation -> {
            PprPlan plan = invocation.getArgument(0);
            plan.setId(UUID.randomUUID());
            return plan;
        });
        when(planRepository.findByIdAndIsDeletedFalse(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            PprPlan plan = new PprPlan();
            plan.setId(id);
            plan.setCode(expectedCode);
            plan.setName("Q2 plan");
            plan.setStartDate(LocalDate.of(2026, 4, 1));
            plan.setEndDate(LocalDate.of(2026, 6, 30));
            plan.setStatus(PlanStatus.DRAFT);
            plan.setDepartmentId(departmentId);
            plan.setCreatedById(createdById);
            plan.setNotes("quarterly");
            plan.setPprType(PprType.PREVENTIVE_MAINTENANCE);
            plan.setScheduleType(PprScheduleType.CALENDAR);
            plan.setScopeType(PprScopeType.DEPARTMENT);
            plan.setTasks(new ArrayList<>());
            plan.setTargets(new ArrayList<>());
            return Optional.of(plan);
        });

        var result = service.create(new com.toir.dto.pprplanning.PprPlanRequest(
                "Q2 plan",
                departmentId,
                createdById,
                "quarterly",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 6, 30)
        ));

        assertThat(result.fromDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(result.toDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(result.pprType()).isEqualTo(PprType.PREVENTIVE_MAINTENANCE);
        assertThat(result.scheduleType()).isEqualTo(PprScheduleType.CALENDAR);
        assertThat(result.frequency()).isNull();
        assertThat(result.scopeType()).isEqualTo(PprScopeType.DEPARTMENT);
        assertThat(result.targets()).isEmpty();
    }

    @Test
    void createPlanAutomaticallyGeneratesTasksAndReturnsReloadedTaskCount() {
        UUID createdById = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        String codePrefix = "PPR-" + Year.now().getValue() + "-";
        String expectedCode = "PPR-" + Year.now().getValue() + "-0001";
        PprTask generatedTask = task(UUID.randomUUID(), null, PprTaskStatus.PLANNED);

        when(planRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(planRepository.existsByCodeAndIsDeletedFalse(expectedCode)).thenReturn(false);
        when(planRepository.saveAndFlush(any(PprPlan.class))).thenAnswer(invocation -> {
            PprPlan plan = invocation.getArgument(0);
            plan.setId(planId);
            return plan;
        });
        when(generatorService.generateForPlan(planId))
                .thenAnswer(invocation -> new PprGeneratorService.GenerationResult(planId, 1, 0));
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenAnswer(invocation -> {
            PprPlan plan = new PprPlan();
            plan.setId(planId);
            plan.setCode(expectedCode);
            plan.setName("Generated plan");
            plan.setStartDate(LocalDate.of(2026, 6, 1));
            plan.setEndDate(LocalDate.of(2026, 6, 30));
            plan.setStatus(PlanStatus.GENERATED);
            plan.setDepartmentId(departmentId);
            plan.setCreatedById(createdById);
            plan.setPprType(PprType.PREVENTIVE_MAINTENANCE);
            plan.setScheduleType(PprScheduleType.CALENDAR);
            plan.setScopeType(PprScopeType.DEPARTMENT);
            generatedTask.setPlan(plan);
            plan.setTasks(new ArrayList<>(List.of(generatedTask)));
            plan.setTargets(new ArrayList<>());
            return Optional.of(plan);
        });

        var result = service.create(new PprPlanRequest(
                "Generated plan",
                departmentId,
                createdById,
                null,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        ));

        assertThat(result.status()).isEqualTo(PlanStatus.GENERATED);
        assertThat(result.taskCount()).isEqualTo(1);
        assertThat(result.tasks()).hasSize(1);
        assertThat(result.generationMessage()).contains("1 PPR task");
        verify(generatorService).generateForPlan(planId);
    }

    @Test
    void createPreventiveCalendarPlanPersistsFrequency() {
        UUID createdById = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        mockPlanCodeSave();

        var result = service.create(new PprPlanRequest(
                "Monthly preventive",
                departmentId,
                createdById,
                "monthly",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                PprType.PREVENTIVE_MAINTENANCE,
                PprScheduleType.CALENDAR,
                PprFrequency.MONTHLY,
                null,
                PprScopeType.DEPARTMENT,
                List.of(),
                List.of()
        ));

        assertThat(result.pprType()).isEqualTo(PprType.PREVENTIVE_MAINTENANCE);
        assertThat(result.scheduleType()).isEqualTo(PprScheduleType.CALENDAR);
        assertThat(result.frequency()).isEqualTo(PprFrequency.MONTHLY);
        assertThat(result.intervalHours()).isNull();
        assertThat(result.scopeType()).isEqualTo(PprScopeType.DEPARTMENT);
    }

    @Test
    void createPlannedRepairOperatingHoursPlanPersistsInterval() {
        mockPlanCodeSave();

        var result = service.create(new PprPlanRequest(
                "Engine-hour repair",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                PprType.PLANNED_REPAIR,
                PprScheduleType.OPERATING_HOURS,
                null,
                10_000L,
                PprScopeType.DEPARTMENT,
                List.of(),
                List.of()
        ));

        assertThat(result.pprType()).isEqualTo(PprType.PLANNED_REPAIR);
        assertThat(result.scheduleType()).isEqualTo(PprScheduleType.OPERATING_HOURS);
        assertThat(result.frequency()).isNull();
        assertThat(result.intervalHours()).isEqualTo(10_000L);
    }

    @Test
    void createOperatingHoursPlanRejectsMissingInterval() {
        assertThatThrownBy(() -> service.create(new PprPlanRequest(
                "Invalid operating-hours",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                PprType.PLANNED_REPAIR,
                PprScheduleType.OPERATING_HOURS,
                null,
                null,
                PprScopeType.DEPARTMENT,
                List.of(),
                List.of()
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("intervalHours must be positive for OPERATING_HOURS PPR schedule");
                });
    }

    @Test
    void createOperatingHoursPlanRejectsNonPositiveInterval() {
        assertThatThrownBy(() -> service.create(new PprPlanRequest(
                "Invalid operating-hours",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                PprType.PLANNED_REPAIR,
                PprScheduleType.OPERATING_HOURS,
                null,
                0L,
                PprScopeType.DEPARTMENT,
                List.of(),
                List.of()
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("intervalHours must be positive for OPERATING_HOURS PPR schedule");
                });
    }

    @Test
    void createCapitalRepairEnterprisePlanAllowsNullDepartment() {
        mockPlanCodeSave();

        var result = service.create(new PprPlanRequest(
                "Enterprise overhaul",
                null,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 10),
                PprType.CAPITAL_REPAIR,
                PprScheduleType.ONE_TIME,
                null,
                null,
                PprScopeType.ENTERPRISE,
                List.of(),
                List.of()
        ));

        assertThat(result.pprType()).isEqualTo(PprType.CAPITAL_REPAIR);
        assertThat(result.scheduleType()).isEqualTo(PprScheduleType.ONE_TIME);
        assertThat(result.scopeType()).isEqualTo(PprScopeType.ENTERPRISE);
        assertThat(result.departmentId()).isNull();
    }

    @Test
    void createDepartmentScopeCapitalRepairRequiresDepartment() {
        assertThatThrownBy(() -> service.create(new PprPlanRequest(
                "Department overhaul",
                null,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 10),
                PprType.CAPITAL_REPAIR,
                PprScheduleType.ONE_TIME,
                null,
                null,
                PprScopeType.DEPARTMENT,
                List.of(),
                List.of()
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("departmentId is required when PPR scopeType is DEPARTMENT");
                });
    }

    @Test
    void createWithEquipmentAndEquipmentTypeTargetsPersistsTargets() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, departmentId);
        EquipmentType equipmentType = equipmentType(equipmentTypeId);
        mockPlanCodeSave();
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of(equipment));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentTypeId))).thenReturn(List.of(equipmentType));

        var result = service.create(new PprPlanRequest(
                "Targeted preventive",
                departmentId,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                PprType.PREVENTIVE_MAINTENANCE,
                PprScheduleType.CALENDAR,
                PprFrequency.MONTHLY,
                null,
                PprScopeType.DEPARTMENT,
                List.of(equipmentId),
                List.of(equipmentTypeId)
        ));

        assertThat(result.targets()).hasSize(2);
        assertThat(result.targets()).anySatisfy(target -> {
            assertThat(target.targetType()).isEqualTo(PprTargetType.EQUIPMENT);
            assertThat(target.equipmentId()).isEqualTo(equipmentId);
            assertThat(target.equipmentTypeId()).isNull();
        });
        assertThat(result.targets()).anySatisfy(target -> {
            assertThat(target.targetType()).isEqualTo(PprTargetType.EQUIPMENT_TYPE);
            assertThat(target.equipmentId()).isNull();
            assertThat(target.equipmentTypeId()).isEqualTo(equipmentTypeId);
        });
    }

    @Test
    void createWithEquipmentTargetRejectsDifferentDepartment() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment(equipmentId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.create(new PprPlanRequest(
                "Wrong department",
                departmentId,
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                PprType.PREVENTIVE_MAINTENANCE,
                PprScheduleType.CALENDAR,
                PprFrequency.MONTHLY,
                null,
                PprScopeType.DEPARTMENT,
                List.of(equipmentId),
                List.of()
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("different department");
                });
    }

    @Test
    void updatePlanReplacesTypeScheduleAndTargets() {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        PprPlan plan = plan(planId, PlanStatus.DRAFT);
        plan.setDepartmentId(departmentId);
        plan.setCreatedById(UUID.randomUUID());
        PprPlanTarget oldTarget = new PprPlanTarget();
        oldTarget.setPlan(plan);
        oldTarget.setTargetType(PprTargetType.EQUIPMENT_TYPE);
        oldTarget.setEquipmentTypeId(UUID.randomUUID());
        plan.getTargets().add(oldTarget);

        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment(equipmentId, departmentId)));
        when(planRepository.saveAndFlush(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update(planId, new PprPlanRequest(
                "Updated plan",
                departmentId,
                UUID.randomUUID(),
                "updated",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                PprType.PLANNED_REPAIR,
                PprScheduleType.OPERATING_HOURS,
                null,
                5_000L,
                PprScopeType.DEPARTMENT,
                List.of(equipmentId),
                List.of()
        ));

        assertThat(result.pprType()).isEqualTo(PprType.PLANNED_REPAIR);
        assertThat(result.scheduleType()).isEqualTo(PprScheduleType.OPERATING_HOURS);
        assertThat(result.intervalHours()).isEqualTo(5_000L);
        assertThat(result.targets()).hasSize(1);
        assertThat(result.targets().getFirst().targetType()).isEqualTo(PprTargetType.EQUIPMENT);
        assertThat(result.targets().getFirst().equipmentId()).isEqualTo(equipmentId);
    }

    @Test
    void updatePlanPreservesTargetsWhenLegacyRequestOmitsTargetFields() {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        PprPlan plan = plan(planId, PlanStatus.DRAFT);
        plan.setDepartmentId(departmentId);
        plan.setCreatedById(UUID.randomUUID());
        PprPlanTarget existingTarget = new PprPlanTarget();
        existingTarget.setPlan(plan);
        existingTarget.setTargetType(PprTargetType.EQUIPMENT_TYPE);
        existingTarget.setEquipmentTypeId(equipmentTypeId);
        plan.getTargets().add(existingTarget);

        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));
        when(planRepository.saveAndFlush(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update(planId, new PprPlanRequest(
                "Legacy update",
                departmentId,
                UUID.randomUUID(),
                "legacy",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        ));

        assertThat(result.targets()).hasSize(1);
        assertThat(result.targets().getFirst().targetType()).isEqualTo(PprTargetType.EQUIPMENT_TYPE);
        assertThat(result.targets().getFirst().equipmentTypeId()).isEqualTo(equipmentTypeId);
    }

    @Test
    void updatePlanRejectsDateRangeWhenToDateIsBeforeFromDate() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, PlanStatus.DRAFT);
        plan.setCreatedById(UUID.randomUUID());
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.update(planId, new com.toir.dto.pprplanning.PprPlanRequest(
                "Invalid range",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 4, 1)
        )))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("PPR plan toDate must not be before fromDate");
                });

        verify(planRepository, never()).saveAndFlush(any(PprPlan.class));
    }

    @Test
    void addTaskSucceedsForDraftAndGeneratedPlans() {
        for (PlanStatus status : new PlanStatus[]{PlanStatus.DRAFT, PlanStatus.GENERATED}) {
            UUID planId = UUID.randomUUID();
            PprPlan plan = plan(planId, status);
            String codePrefix = "PPR-TASK-" + Year.now().getValue() + "-";
            String expectedCode = "PPR-TASK-" + Year.now().getValue() + "-0001";

            when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));
            when(taskRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
            when(taskRepository.existsByCode(expectedCode)).thenReturn(false);
            when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> {
                PprTask task = invocation.getArgument(0);
                task.setId(UUID.randomUUID());
                return task;
            });

            PprTaskDto created = service.addTask(planId, taskRequest());

            assertThat(created.status()).isEqualTo(PprTaskStatus.PLANNED);
        }
    }

    @Test
    void addTaskAcceptsMissingEquipment() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, PlanStatus.DRAFT);
        String codePrefix = "PPR-TASK-" + Year.now().getValue() + "-";
        String expectedCode = "PPR-TASK-" + Year.now().getValue() + "-0001";

        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));
        when(taskRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(taskRepository.existsByCode(expectedCode)).thenReturn(false);
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> {
            PprTask task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        PprTaskDto created = service.addTask(planId, taskRequest(null));

        assertThat(created.equipmentId()).isNull();
        assertThat(created.status()).isEqualTo(PprTaskStatus.PLANNED);
    }

    @Test
    void addTaskBlockedForApprovedInProgressClosedAndCancelledPlans() {
        for (PlanStatus status : new PlanStatus[]{
                PlanStatus.APPROVED,
                PlanStatus.IN_PROGRESS,
                PlanStatus.CLOSED,
                PlanStatus.CANCELLED
        }) {
            UUID planId = UUID.randomUUID();
            when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, status)));

            assertThatThrownBy(() -> service.addTask(planId, taskRequest()))
                    .isInstanceOfSatisfying(RestException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).contains("DRAFT or GENERATED");
                    });
        }

        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void approveTaskBlockedWhenParentPlanIsDraftOrGenerated() {
        for (PlanStatus status : new PlanStatus[]{PlanStatus.DRAFT, PlanStatus.GENERATED}) {
            UUID taskId = UUID.randomUUID();
            when(taskRepository.findByIdAndIsDeletedFalse(taskId))
                    .thenReturn(Optional.of(task(taskId, plan(UUID.randomUUID(), status), PprTaskStatus.PLANNED)));

            assertThatThrownBy(() -> service.approveTask(taskId))
                    .isInstanceOfSatisfying(RestException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).contains("parent PPR plan is approved");
                    });
        }

        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void approveTaskSucceedsWhenParentPlanApproved() {
        UUID taskId = UUID.randomUUID();
        PprTask task = task(taskId, plan(UUID.randomUUID(), PlanStatus.APPROVED), PprTaskStatus.PLANNED);

        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PprTaskDto response = service.approveTask(taskId);

        assertThat(response.status()).isEqualTo(PprTaskStatus.APPROVED);
    }

    @Test
    void startTaskBlockedWhenParentPlanDraftOrGenerated() {
        for (PlanStatus status : new PlanStatus[]{PlanStatus.DRAFT, PlanStatus.GENERATED}) {
            UUID taskId = UUID.randomUUID();
            when(taskRepository.findByIdAndIsDeletedFalse(taskId))
                    .thenReturn(Optional.of(task(taskId, plan(UUID.randomUUID(), status), PprTaskStatus.APPROVED)));

            assertThatThrownBy(() -> service.startTask(taskId))
                    .isInstanceOfSatisfying(RestException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).isEqualTo("Task can start only after the parent PPR plan is approved");
                    });
        }

        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void startTaskBlockedWhenTaskIsPlanned() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndIsDeletedFalse(taskId))
                .thenReturn(Optional.of(task(taskId, plan(UUID.randomUUID(), PlanStatus.APPROVED), PprTaskStatus.PLANNED)));

        assertThatThrownBy(() -> service.startTask(taskId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Only APPROVED PPR tasks can be started");
                });

        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void startTaskSucceedsWhenParentPlanApprovedAndTaskApproved() {
        UUID taskId = UUID.randomUUID();
        PprTask task = task(taskId, plan(UUID.randomUUID(), PlanStatus.APPROVED), PprTaskStatus.APPROVED);

        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));
        when(planRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PprTaskDto response = service.startTask(taskId);

        assertThat(response.status()).isEqualTo(PprTaskStatus.IN_PROGRESS);
    }

    @Test
    void startingTaskUpdatesApprovedParentPlanToInProgress() {
        UUID taskId = UUID.randomUUID();
        PprPlan plan = plan(UUID.randomUUID(), PlanStatus.APPROVED);
        PprTask task = task(taskId, plan, PprTaskStatus.APPROVED);

        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));
        when(planRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.startTask(taskId);

        assertThat(plan.getStatus()).isEqualTo(PlanStatus.IN_PROGRESS);
        verify(planRepository).save(plan);
    }

    @Test
    void completeTaskWithMaterialUsagesIssuesAgainstLinkedWorkOrder() {
        UUID taskId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        PprTask task = task(taskId, plan(UUID.randomUUID(), PlanStatus.IN_PROGRESS), PprTaskStatus.IN_PROGRESS);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setPprTaskId(taskId);
        RepairMaterialUsageDto usage = new RepairMaterialUsageDto(
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                java.math.BigDecimal.valueOf(3),
                8.0
        );

        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));
        when(workOrderRepository.findFirstByPprTaskIdAndIsDeletedFalseOrderByUpdatedAtDesc(taskId))
                .thenReturn(Optional.of(workOrder));
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repairMaterialUsageService.register(workOrderId, usage)).thenReturn(usage);

        PprTaskDto result = service.completeTask(taskId, new CompletePprTaskRequest(4.5, List.of(usage)));

        assertThat(result.status()).isEqualTo(PprTaskStatus.COMPLETED);
        assertThat(result.actualLaborHours()).isEqualTo(4.5);
        verify(repairMaterialUsageService).register(workOrderId, usage);
        verify(taskRepository).save(task);
    }

    @Test
    void completeTaskWithMaterialUsagesRequiresLinkedWorkOrder() {
        UUID taskId = UUID.randomUUID();
        PprTask task = task(taskId, plan(UUID.randomUUID(), PlanStatus.IN_PROGRESS), PprTaskStatus.IN_PROGRESS);
        RepairMaterialUsageDto usage = new RepairMaterialUsageDto(
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                java.math.BigDecimal.valueOf(3),
                8.0
        );

        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));
        when(workOrderRepository.findFirstByPprTaskIdAndIsDeletedFalseOrderByUpdatedAtDesc(taskId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeTask(taskId, new CompletePprTaskRequest(4.5, List.of(usage))))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Material usage requires a linked Work Order");
                });

        assertThat(task.getStatus()).isEqualTo(PprTaskStatus.IN_PROGRESS);
        verify(taskRepository, never()).save(any(PprTask.class));
    }

    private PprPlan plan(UUID id, PlanStatus status) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setStatus(status);
        plan.setTasks(new ArrayList<>());
        plan.setTargets(new ArrayList<>());
        return plan;
    }

    private void mockPlanCodeSave() {
        String codePrefix = "PPR-" + Year.now().getValue() + "-";
        String expectedCode = "PPR-" + Year.now().getValue() + "-0001";
        when(planRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(planRepository.existsByCodeAndIsDeletedFalse(expectedCode)).thenReturn(false);
        final PprPlan[] savedPlan = new PprPlan[1];
        when(planRepository.saveAndFlush(any(PprPlan.class))).thenAnswer(invocation -> {
            PprPlan plan = invocation.getArgument(0);
            plan.setId(UUID.randomUUID());
            savedPlan[0] = plan;
            return plan;
        });
        when(planRepository.findByIdAndIsDeletedFalse(any(UUID.class))).thenAnswer(invocation -> Optional.of(savedPlan[0]));
    }

    @Test
    void findAllUnpagedSortsByStatus() {
        PprPlan approved = plan(UUID.randomUUID(), PlanStatus.APPROVED);
        approved.setStartDate(LocalDate.of(2026, 3, 1));
        approved.setPprType(PprType.CAPITAL_REPAIR);
        PprPlan draft = plan(UUID.randomUUID(), PlanStatus.DRAFT);
        draft.setStartDate(LocalDate.of(2026, 1, 1));
        draft.setPprType(PprType.PREVENTIVE_MAINTENANCE);
        PprPlan generated = plan(UUID.randomUUID(), PlanStatus.GENERATED);
        generated.setStartDate(LocalDate.of(2026, 2, 1));
        generated.setPprType(PprType.PLANNED_REPAIR);

        when(planRepository.searchPlans(null, null, null, null))
                .thenReturn(List.of(approved, draft, generated));

        var byStatus = service.findAllUnpaged(null, null, null, null, "status", "asc");
        var byFromDate = service.findAllUnpaged(null, null, null, null, "fromDate", "asc");
        var byPprType = service.findAllUnpaged(null, null, null, null, "pprType", "desc");

        assertThat(byStatus.getContent()).extracting("status")
                .containsExactly(PlanStatus.DRAFT, PlanStatus.GENERATED, PlanStatus.APPROVED);
        assertThat(byFromDate.getContent()).extracting("fromDate")
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
        assertThat(byPprType.getContent()).extracting("pprType")
                .containsExactly(PprType.CAPITAL_REPAIR, PprType.PLANNED_REPAIR, PprType.PREVENTIVE_MAINTENANCE);
    }

    @Test
    void createWithAllFieldsAndTargetsReloadsPersistedPlanWithNames() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, departmentId);
        equipment.setName("Pump 17");
        EquipmentType equipmentType = equipmentType(equipmentTypeId);
        equipmentType.setName("Pump");
        MaintenanceRegulation regulation = regulation(regulationId, "Monthly inspection");
        mockPlanCodeSave();
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of(equipment));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentTypeId))).thenReturn(List.of(equipmentType));
        when(maintenanceRegulationRepository.findAllByIdInAndIsDeletedFalse(List.of(regulationId))).thenReturn(List.of(regulation));

        var result = service.create(new PprPlanRequest(
                "Full targeted plan",
                departmentId,
                UUID.randomUUID(),
                "all fields",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                PprType.PREVENTIVE_MAINTENANCE,
                PprScheduleType.CALENDAR,
                PprFrequency.MONTHLY,
                null,
                PprScopeType.DEPARTMENT,
                List.of(equipmentId),
                List.of(equipmentTypeId),
                List.of(regulationId)
        ));

        assertThat(result.name()).isEqualTo("Full targeted plan");
        assertThat(result.notes()).isEqualTo("all fields");
        assertThat(result.pprType()).isEqualTo(PprType.PREVENTIVE_MAINTENANCE);
        assertThat(result.frequency()).isEqualTo(PprFrequency.MONTHLY);
        assertThat(result.targets()).hasSize(3);
        assertThat(result.targets()).anySatisfy(target -> {
            assertThat(target.targetType()).isEqualTo(PprTargetType.EQUIPMENT);
            assertThat(target.equipmentId()).isEqualTo(equipmentId);
            assertThat(target.equipmentName()).isEqualTo("Pump 17");
        });
        assertThat(result.targets()).anySatisfy(target -> {
            assertThat(target.targetType()).isEqualTo(PprTargetType.EQUIPMENT_TYPE);
            assertThat(target.equipmentTypeId()).isEqualTo(equipmentTypeId);
            assertThat(target.equipmentTypeName()).isEqualTo("Pump");
        });
        assertThat(result.targets()).anySatisfy(target -> {
            assertThat(target.targetType()).isEqualTo(PprTargetType.REGULATION);
            assertThat(target.regulationId()).isEqualTo(regulationId);
            assertThat(target.regulationName()).isEqualTo("Monthly inspection");
        });
        verify(planRepository).saveAndFlush(any(PprPlan.class));
        verify(planRepository).findByIdAndIsDeletedFalse(result.id());
    }

    private Equipment equipment(UUID id, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setDepartmentId(departmentId);
        return equipment;
    }

    private EquipmentType equipmentType(UUID id) {
        EquipmentType equipmentType = new EquipmentType();
        equipmentType.setId(id);
        return equipmentType;
    }

    private MaintenanceRegulation regulation(UUID id, String name) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(id);
        regulation.setName(name);
        return regulation;
    }

    private PprTask task(UUID id, PprPlan plan, PprTaskStatus status) {
        PprTask task = new PprTask();
        task.setId(id);
        task.setCode("PPR-TASK-2026-0001");
        task.setPlan(plan);
        task.setRegulationId(UUID.randomUUID());
        task.setEquipmentId(UUID.randomUUID());
        task.setTitle("PPR task");
        task.setScheduledStart(LocalDateTime.of(2026, 6, 1, 9, 0));
        task.setScheduledEnd(LocalDateTime.of(2026, 6, 1, 12, 0));
        task.setDueDate(LocalDateTime.of(2026, 6, 2, 9, 0));
        task.setStatus(status);
        task.setPriority(PriorityLevel.MEDIUM);
        task.setPlannedLaborHours(2.0);
        return task;
    }

    private PprTaskRequest taskRequest() {
        return taskRequest(UUID.randomUUID());
    }

    private PprTaskRequest taskRequest(UUID equipmentId) {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 0);
        return new PprTaskRequest(
                null,
                UUID.randomUUID(),
                equipmentId,
                "Manual PPR task",
                null,
                null,
                start,
                start.plusHours(2),
                start.plusDays(1),
                PriorityLevel.MEDIUM,
                2.0
        );
    }
}
