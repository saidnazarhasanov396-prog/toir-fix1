package com.toir.service;

import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRegulationConditionOperator;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PlanStatus;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.service.maintanance.MaintenanceDueCalculationService;
import com.toir.service.maintanance.PprTaskScheduleWindowCalculator;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PprGeneratorDynamicConditionTest {

    @Mock
    PprPlanRepository planRepository;

    @Mock
    PprTaskRepository taskRepository;

    @Mock
    MaintenanceRegulationRepository regulationRepository;

    @Mock
    EquipmentMaintenanceRuleRepository equipmentMaintenanceRuleRepository;

    @Mock
    MaintenanceRegulationAttributeConditionRepository conditionRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentAttributeDefinitionRepository attributeDefinitionRepository;

    @Mock
    EquipmentAttributeValueRepository attributeValueRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    MaintenanceDueCalculationService maintenanceDueCalculationService;

    @Spy
    PprTaskScheduleWindowCalculator scheduleWindowCalculator =
            new PprTaskScheduleWindowCalculator();

    @InjectMocks
    PprGeneratorService service;

    @Test
    void generateForPlanWithMotorPowerGreaterThan50CreatesTaskOnlyForMatchingPump() {
        UUID typeId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID motorPowerDefinitionId = UUID.randomUUID();
        Equipment p101 = equipment("P-101", typeId);
        Equipment p102 = equipment("P-102", typeId);
        stubGeneration(planId, typeId, regulationId, motorPowerDefinitionId, List.of(p101, p102),
                List.of(condition(regulationId, "motor_power", MaintenanceRegulationConditionOperator.GREATER_THAN, 50.0)));

        PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<PprTask> taskCaptor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getEquipmentId()).isEqualTo(p101.getId());
        assertThat(taskCaptor.getValue().getTitle()).contains("P-101");
    }

    @Test
    void generateForPlanWithoutConditionsKeepsPreviousTypeOnlyBehavior() {
        UUID typeId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID motorPowerDefinitionId = UUID.randomUUID();
        Equipment p101 = equipment("P-101", typeId);
        Equipment p102 = equipment("P-102", typeId);
        stubGeneration(planId, typeId, regulationId, motorPowerDefinitionId, List.of(p101, p102), List.of());

        PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

        assertThat(result.created()).isEqualTo(2);
        verify(taskRepository, times(2)).save(any(PprTask.class));
    }

    @ParameterizedTest
    @EnumSource(value = MaintenanceRegulationConditionOperator.class, names = {
            "EQUALS",
            "NOT_EQUALS",
            "GREATER_THAN",
            "GREATER_THAN_OR_EQUALS",
            "LESS_THAN",
            "LESS_THAN_OR_EQUALS",
            "EXISTS",
            "NOT_EXISTS"
    })
    void generateForPlanSupportsAllNumericConditionOperators(MaintenanceRegulationConditionOperator operator) {
        UUID typeId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID motorPowerDefinitionId = UUID.randomUUID();
        Equipment p101 = equipment("P-101", typeId);
        Equipment p102 = equipment("P-102", typeId);

        Double expected = switch (operator) {
            case EQUALS, NOT_EQUALS -> 75.0;
            case GREATER_THAN -> 50.0;
            case GREATER_THAN_OR_EQUALS -> 75.0;
            case LESS_THAN -> 50.0;
            case LESS_THAN_OR_EQUALS -> 30.0;
            case EXISTS, NOT_EXISTS -> null;
        };
        int expectedCreated = switch (operator) {
            case EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUALS, LESS_THAN, LESS_THAN_OR_EQUALS -> 1;
            case NOT_EQUALS -> 1;
            case EXISTS -> 2;
            case NOT_EXISTS -> 0;
        };
        stubGeneration(planId, typeId, regulationId, motorPowerDefinitionId, List.of(p101, p102),
                List.of(condition(regulationId, "motor_power", operator, expected)));

        PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

        assertThat(result.created()).isEqualTo(expectedCreated);
        verify(taskRepository, times(expectedCreated)).save(any(PprTask.class));
    }

    private void stubGeneration(UUID planId,
                                UUID typeId,
                                UUID regulationId,
                                UUID motorPowerDefinitionId,
                                List<Equipment> equipment,
                                List<MaintenanceRegulationAttributeCondition> conditions) {
        PprPlan plan = plan(planId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId);
        EquipmentAttributeDefinition motorPower = definition(motorPowerDefinitionId, typeId, "motor_power");

        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId)).thenReturn(Optional.of(plan));
        when(regulationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(regulation));
        when(equipmentMaintenanceRuleRepository.findAllActive()).thenReturn(List.of());
        when(conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(List.of(regulationId))).thenReturn(conditions);
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(equipment);
        when(attributeDefinitionRepository.findAllByEquipmentTypeIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of(motorPower));
        when(attributeValueRepository.findAllByEquipmentIdInAndIsDeletedFalse(anyCollection())).thenReturn(equipment.stream()
                .map(e -> value(e.getId(), motorPowerDefinitionId, e.getCode().equals("P-101") ? 75.0 : 30.0))
                .toList());
        when(taskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        lenient().when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> {
            PprTask task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });
        lenient().when(planRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PprPlan plan(UUID id) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("May PPR plan");
        plan.setStartDate(LocalDate.of(2026, 5, 1));
        plan.setEndDate(LocalDate.of(2026, 5, 31));
        plan.setStatus(PlanStatus.DRAFT);
        plan.setCreatedById(UUID.randomUUID());
        return plan;
    }

    private MaintenanceRegulation regulation(UUID id, UUID typeId) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(id);
        regulation.setCode("MR-2026-0001");
        regulation.setName("High-power pump monthly service");
        regulation.setEquipmentTypeId(typeId);
        regulation.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        regulation.setNormativeLaborHours(4.0);
        regulation.setActive(true);
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setRequiresShutdown(false);
        return regulation;
    }

    private Equipment equipment(String code, UUID typeId) {
        Equipment equipment = new Equipment();
        equipment.setId(UUID.randomUUID());
        equipment.setCode(code);
        equipment.setName("Pump " + code);
        equipment.setInventoryNumber("INV-" + code);
        equipment.setEquipmentTypeId(typeId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private EquipmentAttributeDefinition definition(UUID id, UUID typeId, String key) {
        EquipmentAttributeDefinition definition = new EquipmentAttributeDefinition();
        definition.setId(id);
        definition.setEquipmentTypeId(typeId);
        definition.setKey(key);
        definition.setLabel("Motor Power");
        definition.setDataType(EquipmentAttributeDataType.NUMBER);
        definition.setRequired(true);
        return definition;
    }

    private EquipmentAttributeValue value(UUID equipmentId, UUID definitionId, Double motorPower) {
        EquipmentAttributeValue value = new EquipmentAttributeValue();
        value.setId(UUID.randomUUID());
        value.setEquipmentId(equipmentId);
        value.setAttributeDefinitionId(definitionId);
        value.setValueNumber(motorPower);
        return value;
    }

    private MaintenanceRegulationAttributeCondition condition(UUID regulationId,
                                                             String key,
                                                             MaintenanceRegulationConditionOperator operator,
                                                             Double value) {
        MaintenanceRegulationAttributeCondition condition = new MaintenanceRegulationAttributeCondition();
        condition.setId(UUID.randomUUID());
        condition.setRegulationId(regulationId);
        condition.setAttributeKey(key);
        condition.setOperator(operator);
        condition.setValueNumber(value);
        return condition;
    }
}
