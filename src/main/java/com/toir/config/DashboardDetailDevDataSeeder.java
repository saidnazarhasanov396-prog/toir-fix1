package com.toir.config;

import com.toir.entity.ConditionReading;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.users.User;
import com.toir.enums.ConditionParameter;
import com.toir.enums.DepartmentType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.users.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores a repeatable local TOIR workflow in the domain tables exported by the detail API. */
@Component
@Profile("dev")
@Order(20)
@ConditionalOnProperty(name = "app.dashboard-detail.dev-data-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DashboardDetailDevDataSeeder implements CommandLineRunner {

    private static final String DEPARTMENT_CODE = "DEV-TOIR-MAINTENANCE";
    private static final String TYPE_CODE = "DEV-TOIR-CENTRIFUGAL-PUMP";
    private static final String EQUIPMENT_CODE = "DEV-TOIR-PUMP-101";
    private static final String WORK_ORDER_NUMBER = "DEV-TOIR-WO-001";

    private final DepartmentRepository departments;
    private final EquipmentTypeRepository equipmentTypes;
    private final EquipmentRepository equipment;
    private final WorkOrderRepository workOrders;
    private final ConditionReadingRepository readings;
    private final UserRepository users;

    @Override
    @Transactional
    public void run(String... args) {
        Department department = departments.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(row -> DEPARTMENT_CODE.equals(row.getCode()))
                .findFirst()
                .orElseGet(() -> departments.save(Department.builder()
                        .code(DEPARTMENT_CODE)
                        .name("Development maintenance workshop")
                        .nameEn("Development maintenance workshop")
                        .nameUz("Development maintenance workshop")
                        .type(DepartmentType.WORKSHOP)
                        .description("Dashboard detail development data")
                        .build()));

        EquipmentType type = equipmentTypes.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(row -> TYPE_CODE.equals(row.getCode()))
                .findFirst()
                .orElseGet(() -> equipmentTypes.save(EquipmentType.builder()
                        .code(TYPE_CODE)
                        .name("Development centrifugal pump")
                        .nameEn("Development centrifugal pump")
                        .nameUz("Development centrifugal pump")
                        .category("ROTATING")
                        .description("Dashboard detail development data")
                        .build()));

        Equipment pump = equipment.findByCodeAndIsDeletedFalse(EQUIPMENT_CODE).orElseGet(() ->
                equipment.save(Equipment.builder()
                        .code(EQUIPMENT_CODE)
                        .name("Ammonia feed pump P-101")
                        .inventoryNumber("DEV-INV-P-101")
                        .technicalNumber("DEV-TECH-P-101")
                        .serialNumber("DEV-SERIAL-P-101")
                        .model("CFP-200")
                        .producedYear(2022)
                        .equipmentTypeId(type.getId())
                        .departmentId(department.getId())
                        .responsibleDepartmentId(department.getId())
                        .manufacturer("TOIR Dev")
                        .status(EquipmentStatus.ACTIVE)
                        .category(EquipmentCategory.PRODUCTION_EQUIPMENT)
                        .commissionedAt(LocalDate.now().minusYears(3))
                        .arrivalDate(LocalDate.now().minusYears(3).minusMonths(1))
                        .hasWarranty(false)
                        .daysOfResourceRemaining(94L)
                        .description("Development operating asset")
                        .build()));

        if (!workOrders.existsByNumberAndIsDeletedFalse(WORK_ORDER_NUMBER)) {
            Instant now = Instant.now();
            User actor = users.findByUsernameAndIsDeletedFalse("admin")
                    .orElseThrow(() -> new IllegalStateException("TOIR dev admin must be bootstrapped before detail data"));
            WorkOrder workOrder = WorkOrder.builder()
                    .number(WORK_ORDER_NUMBER)
                    .title("Inspect pump bearing vibration")
                    .equipmentId(pump.getId())
                    .departmentId(department.getId())
                    .requiresShutdown(false)
                    .requiresIsolation(false)
                    .status(WorkOrderStatus.IN_PROGRESS)
                    .type(WorkOrderType.INSPECTION)
                    .workType(WorkType.DIAGNOSTICS)
                    .priority(PriorityLevel.HIGH)
                    .startPlannedAt(now.minus(2, ChronoUnit.HOURS))
                    .endPlannedAt(now.plus(4, ChronoUnit.HOURS))
                    .startedAt(now.minus(1, ChronoUnit.HOURS))
                    .summary("Vibration exceeded warning threshold")
                    .repairActRequired(false)
                    .stoppageActRequired(false)
                    .build();
            workOrder.setCreatedById(actor.getId());
            workOrder.setUpdatedById(actor.getId());
            workOrders.save(workOrder);
        }

        boolean vibrationPresent = readings.findAllByEquipmentIdAndIsDeletedFalseOrderByRecordedAtDesc(pump.getId())
                .stream()
                .anyMatch(row -> row.getParameter() == ConditionParameter.VIBRATION);
        if (!vibrationPresent) {
            readings.save(ConditionReading.builder()
                    .equipmentId(pump.getId())
                    .parameter(ConditionParameter.VIBRATION)
                    .value(6.8D)
                    .unit("mm/s")
                    .recordedAt(Instant.now().minusSeconds(900))
                    .warnHigh(5.0D)
                    .alarmHigh(8.0D)
                    .severity("WARN")
                    .notes("Dashboard detail development reading")
                    .build());
        }
    }
}
