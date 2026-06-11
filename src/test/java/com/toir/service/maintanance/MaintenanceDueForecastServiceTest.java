package com.toir.service.maintanance;

import com.toir.dto.maintenancedueforecast.MaintenanceDueForecastResponse;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.DepartmentType;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterType;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceDueForecastServiceTest {

    @Mock
    MaintenanceDueEventRepository dueEventRepository;
    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    EquipmentTypeRepository equipmentTypeRepository;
    @Mock
    DepartmentRepository departmentRepository;
    @Mock
    MaintenanceRegulationRepository regulationRepository;
    @Mock
    EquipmentMaintenanceRuleRepository ruleRepository;
    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    MaintenanceDueForecastService service;

    @Test
    void groupsForecastIntoOverdueSevenThirtyAndNinetyDayBuckets() {
        LocalDate today = LocalDate.of(2026, 6, 8);
        UUID departmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), "EQ-001", "Pump", equipmentTypeId, departmentId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), equipmentTypeId, MaintenanceTriggerPolicy.ANY);

        MaintenanceDueEvent overdue = event(UUID.randomUUID(), equipment.getId(), regulation.getId(),
                MaintenanceDueStatus.OVERDUE, today.minusDays(2), MaintenanceDueEventStatus.DETECTED);
        MaintenanceDueEvent next7 = event(UUID.randomUUID(), equipment.getId(), regulation.getId(),
                MaintenanceDueStatus.UPCOMING, today.plusDays(3), MaintenanceDueEventStatus.DETECTED);
        MaintenanceDueEvent next30 = event(UUID.randomUUID(), equipment.getId(), regulation.getId(),
                MaintenanceDueStatus.DUE, today.plusDays(20), MaintenanceDueEventStatus.AWAITING_APPROVAL);
        MaintenanceDueEvent next90 = event(UUID.randomUUID(), equipment.getId(), regulation.getId(),
                MaintenanceDueStatus.UPCOMING, today.plusDays(60), MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        next90.setCreatedWorkOrderId(UUID.randomUUID());

        stubForecast(List.of(overdue, next7, next30, next90), List.of(equipment), List.of(regulation), List.of(), departmentId);

        MaintenanceDueForecastResponse result = service.getForecast(
                today, today.plusDays(90), departmentId, null, null, null, null);

        assertThat(result.counters().overdueCount()).isEqualTo(1);
        assertThat(result.counters().upcomingCount()).isEqualTo(2);
        assertThat(result.counters().dueCount()).isEqualTo(1);
        assertThat(result.counters().approvalPendingCount()).isEqualTo(1);
        assertThat(result.counters().woCreatedCount()).isEqualTo(1);
        assertThat(result.buckets().overdue()).extracting(MaintenanceDueForecastResponse.Row::dueEventId)
                .containsExactly(overdue.getId());
        assertThat(result.buckets().next7Days()).extracting(MaintenanceDueForecastResponse.Row::dueEventId)
                .containsExactly(next7.getId());
        assertThat(result.buckets().next30Days()).extracting(MaintenanceDueForecastResponse.Row::dueEventId)
                .containsExactly(next30.getId());
        assertThat(result.buckets().next90Days()).extracting(MaintenanceDueForecastResponse.Row::dueEventId)
                .containsExactly(next90.getId());
        assertThat(result.rows()).hasSize(4);
    }

    @Test
    void blockedEventIncludesFixMetadata() {
        LocalDate today = LocalDate.of(2026, 6, 8);
        UUID departmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID(), "EQ-002", "Compressor", equipmentTypeId, departmentId);
        MaintenanceDueEvent blocked = event(UUID.randomUUID(), equipment.getId(), null,
                MaintenanceDueStatus.BLOCKED, today.plusDays(1), MaintenanceDueEventStatus.DETECTED);
        blocked.setMeterType(MeterType.ENGINE_HOURS);
        blocked.setExplanation("Meter data is missing");

        stubForecast(List.of(blocked), List.of(equipment), List.of(), List.of(), departmentId);

        MaintenanceDueForecastResponse result = service.getForecast(
                today, today.plusDays(90), departmentId, null, null, null, null);

        assertThat(result.counters().blockedCount()).isEqualTo(1);
        assertThat(result.buckets().blocked()).singleElement().satisfies(row -> {
            assertThat(row.blockingCode()).isEqualTo("MISSING_ACTIVE_METER");
            assertThat(row.blockingField()).isEqualTo("ENGINE_HOURS");
            assertThat(row.fixLink()).isEqualTo("/equipment/" + equipment.getId() + "/meters");
            assertThat(row.structuredExplanationSummary()).isEqualTo("Meter data is missing");
        });
    }

    @Test
    void filtersByDepartmentEquipmentTypeStatusCriticalityAndTriggerPolicy() {
        LocalDate today = LocalDate.of(2026, 6, 8);
        UUID departmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID criticalityId = UUID.randomUUID();
        Equipment includedEquipment = equipment(UUID.randomUUID(), "EQ-IN", "Included", equipmentTypeId, departmentId);
        includedEquipment.setCriticalityClassId(criticalityId);
        Equipment otherDepartmentEquipment = equipment(UUID.randomUUID(), "EQ-OUT", "Other department", equipmentTypeId, otherDepartmentId);
        otherDepartmentEquipment.setCriticalityClassId(criticalityId);
        MaintenanceRegulation anyRegulation = regulation(UUID.randomUUID(), equipmentTypeId, MaintenanceTriggerPolicy.ANY);
        MaintenanceRegulation allRegulation = regulation(UUID.randomUUID(), equipmentTypeId, MaintenanceTriggerPolicy.ALL);
        MaintenanceDueEvent included = event(UUID.randomUUID(), includedEquipment.getId(), anyRegulation.getId(),
                MaintenanceDueStatus.UPCOMING, today.plusDays(5), MaintenanceDueEventStatus.DETECTED);
        MaintenanceDueEvent wrongTrigger = event(UUID.randomUUID(), includedEquipment.getId(), allRegulation.getId(),
                MaintenanceDueStatus.UPCOMING, today.plusDays(6), MaintenanceDueEventStatus.DETECTED);
        MaintenanceDueEvent wrongDepartment = event(UUID.randomUUID(), otherDepartmentEquipment.getId(), anyRegulation.getId(),
                MaintenanceDueStatus.UPCOMING, today.plusDays(7), MaintenanceDueEventStatus.DETECTED);

        stubForecast(List.of(included, wrongTrigger, wrongDepartment),
                List.of(includedEquipment, otherDepartmentEquipment),
                List.of(anyRegulation, allRegulation),
                List.of(),
                departmentId);

        MaintenanceDueForecastResponse result = service.getForecast(
                today,
                today.plusDays(90),
                departmentId,
                equipmentTypeId,
                MaintenanceDueStatus.UPCOMING,
                criticalityId,
                MaintenanceTriggerPolicy.ANY);

        assertThat(result.rows()).singleElement()
                .satisfies(row -> assertThat(row.dueEventId()).isEqualTo(included.getId()));
    }

    private void stubForecast(List<MaintenanceDueEvent> events,
                              List<Equipment> equipment,
                              List<MaintenanceRegulation> regulations,
                              List<EquipmentMaintenanceRule> rules,
                              UUID enforcedDepartmentId) {
        when(scopeAccessService.enforceDepartmentScope(enforcedDepartmentId)).thenReturn(enforcedDepartmentId);
        when(dueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(events);
        if (!equipment.isEmpty()) {
            when(equipmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(equipment);
        }
        if (!regulations.isEmpty()) {
            when(regulationRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(regulations);
        }
        if (!rules.isEmpty()) {
            when(ruleRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(rules);
        }
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(equipment.stream()
                        .map(Equipment::getEquipmentTypeId)
                        .distinct()
                        .map(id -> equipmentType(id, "TYPE", "Type"))
                        .toList());
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(equipment.stream()
                        .map(item -> item.getResponsibleDepartmentId() != null
                                ? item.getResponsibleDepartmentId()
                                : item.getDepartmentId())
                        .distinct()
                        .map(id -> department(id, "DEP", "Department"))
                        .toList());
    }

    private MaintenanceDueEvent event(UUID id,
                                      UUID equipmentId,
                                      UUID regulationId,
                                      MaintenanceDueStatus dueStatus,
                                      LocalDate dueDate,
                                      MaintenanceDueEventStatus status) {
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        event.setId(id);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setStatus(status);
        event.setDueStatus(dueStatus);
        event.setTriggerSource(MaintenanceTriggerSource.CALENDAR_JOB);
        event.setCycleKey("cycle-" + id);
        event.setDueAt(dueDate.atStartOfDay().toInstant(ZoneOffset.UTC));
        event.setDetectedAt(dueDate.minusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC));
        event.setExplanation("Due summary");
        return event;
    }

    private Equipment equipment(UUID id, String code, String name, UUID equipmentTypeId, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode(code);
        equipment.setName(name);
        equipment.setInventoryNumber(code + "-INV");
        equipment.setEquipmentTypeId(equipmentTypeId);
        equipment.setResponsibleDepartmentId(departmentId);
        return equipment;
    }

    private EquipmentType equipmentType(UUID id, String code, String name) {
        EquipmentType type = new EquipmentType();
        type.setId(id);
        type.setCode(code);
        type.setName(name);
        return type;
    }

    private Department department(UUID id, String code, String name) {
        Department department = new Department();
        department.setId(id);
        department.setCode(code);
        department.setName(name);
        department.setType(DepartmentType.WORKSHOP);
        return department;
    }

    private MaintenanceRegulation regulation(UUID id, UUID equipmentTypeId, MaintenanceTriggerPolicy triggerPolicy) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(id);
        regulation.setEquipmentTypeId(equipmentTypeId);
        regulation.setCode("REG");
        regulation.setName("Regulation");
        regulation.setTriggerPolicy(triggerPolicy);
        return regulation;
    }
}
