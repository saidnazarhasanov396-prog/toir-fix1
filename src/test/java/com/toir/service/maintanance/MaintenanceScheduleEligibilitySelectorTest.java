package com.toir.service.maintanance;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentCommissioningAct;
import com.toir.entity.equipment.EquipmentType;
import com.toir.enums.EquipmentCommissioningStatus;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.repository.equipment.EquipmentCommissioningActRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.test.RepositorySliceTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
@Import(MaintenanceScheduleEligibilitySelector.class)
class MaintenanceScheduleEligibilitySelectorTest {

    @Autowired
    MaintenanceScheduleEligibilitySelector selector;

    @Autowired
    EquipmentRepository equipmentRepository;

    @Autowired
    EquipmentTypeRepository equipmentTypeRepository;

    @Autowired
    EquipmentCommissioningActRepository commissioningActRepository;

    @Test
    void equipmentOptionsRequireEveryOperationalCriterionAndDepartmentScope() {
        UUID departmentId = UUID.randomUUID();
        EquipmentType type = saveType("TYPE-1", "Насос");

        Equipment eligible = saveEquipment(
                "EQ-ELIGIBLE", type.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        );
        approve(eligible);

        Equipment inactive = saveEquipment(
                "EQ-INACTIVE", type.getId(), departmentId,
                EquipmentStatus.STANDBY, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        );
        approve(inactive);

        Equipment outsideDepartment = saveEquipment(
                "EQ-WAREHOUSE", type.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.WAREHOUSE,
                LocalDate.of(2024, 1, 1)
        );
        approve(outsideDepartment);

        Equipment withoutOperationStart = saveEquipment(
                "EQ-NO-START", type.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT, null
        );
        approve(withoutOperationStart);

        Equipment withoutApprovedAct = saveEquipment(
                "EQ-NO-ACT", type.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        );
        commissioningActRepository.saveAndFlush(act(
                withoutApprovedAct,
                EquipmentCommissioningStatus.PENDING_APPROVAL,
                false
        ));

        Equipment withDeletedAct = saveEquipment(
                "EQ-DELETED-ACT", type.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        );
        commissioningActRepository.saveAndFlush(act(
                withDeletedAct,
                EquipmentCommissioningStatus.APPROVED,
                true
        ));

        Equipment otherDepartment = saveEquipment(
                "EQ-OTHER-DEPT", type.getId(), UUID.randomUUID(),
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        );
        approve(otherDepartment);

        var page = selector.findOptions(
                MaintenanceScheduleScopeType.EQUIPMENT,
                departmentId,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(page.getContent())
                .extracting(option -> option.code())
                .containsExactly("EQ-ELIGIBLE");
        assertThat(page.getContent().getFirst().eligibleEquipmentCount()).isEqualTo(1);
    }

    @Test
    void typeOptionsHideEmptyTypesCountEligibleEquipmentAndPageDeterministically() {
        UUID departmentId = UUID.randomUUID();
        EquipmentType first = saveType("TYPE-A", "Агрегат");
        EquipmentType second = saveType("TYPE-B", "Бензин");
        EquipmentType empty = saveType("TYPE-C", "Пустой тип");

        approve(saveEquipment(
                "EQ-A-1", first.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        ));
        approve(saveEquipment(
                "EQ-A-2", first.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        ));
        approve(saveEquipment(
                "EQ-A-OTHER", first.getId(), UUID.randomUUID(),
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        ));
        approve(saveEquipment(
                "EQ-B-1", second.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        ));
        saveEquipment(
                "EQ-C-INACTIVE", empty.getId(), departmentId,
                EquipmentStatus.STANDBY, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        );

        var firstPage = selector.findOptions(
                MaintenanceScheduleScopeType.EQUIPMENT_TYPE,
                departmentId,
                "type",
                PageRequest.of(0, 1)
        );
        var secondPage = selector.findOptions(
                MaintenanceScheduleScopeType.EQUIPMENT_TYPE,
                departmentId,
                "type",
                PageRequest.of(1, 1)
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.code()).isEqualTo("TYPE-A");
                    assertThat(option.eligibleEquipmentCount()).isEqualTo(2);
                });
        assertThat(secondPage.getContent())
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.code()).isEqualTo("TYPE-B");
                    assertThat(option.eligibleEquipmentCount()).isEqualTo(1);
                });
    }

    private EquipmentType saveType(String code, String name) {
        EquipmentType type = new EquipmentType();
        type.setCode(code);
        type.setName(name);
        type.setCategory("TEST");
        return equipmentTypeRepository.saveAndFlush(type);
    }

    private Equipment saveEquipment(
            String code,
            UUID equipmentTypeId,
            UUID departmentId,
            EquipmentStatus status,
            EquipmentLocationType locationType,
            LocalDate operationStartDate
    ) {
        Equipment equipment = new Equipment();
        equipment.setCode(code);
        equipment.setName("Equipment " + code);
        equipment.setInventoryNumber("INV-" + code);
        equipment.setEquipmentTypeId(equipmentTypeId);
        equipment.setDepartmentId(departmentId);
        equipment.setResponsibleDepartmentId(departmentId);
        equipment.setStatus(status);
        equipment.setCurrentLocationType(locationType);
        equipment.setOperationStartDate(operationStartDate);
        return equipmentRepository.saveAndFlush(equipment);
    }

    private void approve(Equipment equipment) {
        commissioningActRepository.saveAndFlush(act(
                equipment,
                EquipmentCommissioningStatus.APPROVED,
                false
        ));
    }

    private EquipmentCommissioningAct act(
            Equipment equipment,
            EquipmentCommissioningStatus status,
            boolean deleted
    ) {
        EquipmentCommissioningAct act = new EquipmentCommissioningAct();
        act.setEquipmentId(equipment.getId());
        act.setSourceWarehouseId(UUID.randomUUID());
        act.setWarehouseItemId(UUID.randomUUID());
        act.setTargetDepartmentId(equipment.getDepartmentId());
        act.setResponsibleEmployeeId(UUID.randomUUID());
        act.setActNumber("ACT-" + UUID.randomUUID());
        act.setActDate(LocalDate.of(2024, 1, 1));
        act.setCommissionedAt(LocalDate.of(2024, 1, 1));
        act.setOperationStartDate(LocalDate.of(2024, 1, 1));
        act.setStatus(status);
        act.setDeleted(deleted);
        return act;
    }
}
