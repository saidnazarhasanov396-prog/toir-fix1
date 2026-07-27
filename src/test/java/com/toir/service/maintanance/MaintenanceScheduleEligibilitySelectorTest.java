package com.toir.service.maintanance;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceScheduleScopeType;
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

    @Test
    void equipmentOptionsRequireOnlyActiveStatusWithinOptionalDepartmentScope() {
        UUID departmentId = UUID.randomUUID();
        EquipmentType type = saveType("TYPE-1", "Насос");

        saveEquipment(
                "EQ-ACTIVE-WAREHOUSE", type.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.WAREHOUSE, null
        );
        saveEquipment(
                "EQ-INACTIVE", type.getId(), departmentId,
                EquipmentStatus.STANDBY, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        );
        saveEquipment(
                "EQ-OTHER-DEPT", type.getId(), UUID.randomUUID(),
                EquipmentStatus.ACTIVE, EquipmentLocationType.OUTSIDE_FACILITY, null
        );

        var departmentPage = selector.findOptions(
                MaintenanceScheduleScopeType.EQUIPMENT,
                departmentId,
                null,
                PageRequest.of(0, 20)
        );
        var enterprisePage = selector.findOptions(
                MaintenanceScheduleScopeType.EQUIPMENT,
                null,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(departmentPage.getContent())
                .extracting(option -> option.code())
                .containsExactly("EQ-ACTIVE-WAREHOUSE");
        assertThat(enterprisePage.getContent())
                .extracting(option -> option.code())
                .containsExactly("EQ-ACTIVE-WAREHOUSE", "EQ-OTHER-DEPT");
    }

    @Test
    void typeOptionsHideEmptyTypesCountEligibleEquipmentAndPageDeterministically() {
        UUID departmentId = UUID.randomUUID();
        EquipmentType first = saveType("TYPE-A", "Агрегат");
        EquipmentType second = saveType("TYPE-B", "Бензин");
        EquipmentType empty = saveType("TYPE-C", "Пустой тип");

        saveEquipment(
                "EQ-A-1", first.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.WAREHOUSE, null
        );
        saveEquipment(
                "EQ-A-2", first.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.OUTSIDE_FACILITY, null
        );
        saveEquipment(
                "EQ-A-OTHER", first.getId(), UUID.randomUUID(),
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT,
                LocalDate.of(2024, 1, 1)
        );
        saveEquipment(
                "EQ-B-1", second.getId(), departmentId,
                EquipmentStatus.ACTIVE, EquipmentLocationType.DEPARTMENT,
                null
        );
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

}
