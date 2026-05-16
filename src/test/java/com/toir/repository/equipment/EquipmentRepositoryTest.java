package com.toir.repository.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",

        // o'zingdagi local test DB ga mosla
        "spring.datasource.url=jdbc:postgresql://localhost:5433/toir",
        "spring.datasource.username=postgres",
        "spring.datasource.password=root123",

        "spring.datasource.hikari.connection-timeout=3000"
})
class EquipmentRepositoryTest {
    @Autowired
    EquipmentRepository repository;

    @Test
    void getEquipmentStatsWithoutFiltersCountsAllNonDeletedEquipmentByStatus() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();

        repository.save(equipment(
                "EQ-001",
                "Main Pump",
                "INV-001",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                departmentId,
                equipmentTypeId
        ));

        repository.save(equipment(
                "EQ-002",
                "Backup Pump",
                "INV-002",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                departmentId,
                equipmentTypeId
        ));

        repository.save(equipment(
                "EQ-003",
                "Broken Compressor",
                "INV-003",
                EquipmentStatus.IN_REPAIR,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                departmentId,
                equipmentTypeId
        ));

        repository.save(equipment(
                "EQ-004",
                "Old Motor",
                "INV-004",
                EquipmentStatus.DECOMMISSIONED,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                departmentId,
                equipmentTypeId
        ));

        EquipmentStatsProjection stats = repository.getEquipmentStats(
                null,
                null,
                null,
                null,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        );

        assertThat(stats.getTotalInRegistry()).isEqualTo(4);
        assertThat(stats.getActive()).isEqualTo(2);
        assertThat(stats.getInRepair()).isEqualTo(1);
        assertThat(stats.getDecommissioned()).isEqualTo(1);
    }

    @Test
    void getEquipmentStatsWithFiltersCountsOnlyMatchingEquipment() {
        UUID targetDepartmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        UUID targetEquipmentTypeId = UUID.randomUUID();
        UUID otherEquipmentTypeId = UUID.randomUUID();

        repository.save(equipment(
                "EQ-PUMP-001",
                "Water Pump",
                "INV-PUMP-001",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                targetDepartmentId,
                targetEquipmentTypeId
        ));

        repository.save(equipment(
                "EQ-PUMP-002",
                "Oil Pump",
                "INV-PUMP-002",
                EquipmentStatus.IN_REPAIR,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                targetDepartmentId,
                targetEquipmentTypeId
        ));

        repository.save(equipment(
                "EQ-COMP-001",
                "Compressor",
                "INV-COMP-001",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                targetDepartmentId,
                targetEquipmentTypeId
        ));

        repository.save(equipment(
                "EQ-PUMP-003",
                "Other Department Pump",
                "INV-PUMP-003",
                EquipmentStatus.ACTIVE,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                otherDepartmentId,
                targetEquipmentTypeId
        ));

        repository.save(equipment(
                "EQ-PUMP-004",
                "Other Type Pump",
                "INV-PUMP-004",
                EquipmentStatus.DECOMMISSIONED,
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                targetDepartmentId,
                otherEquipmentTypeId
        ));

        EquipmentStatsProjection stats = repository.getEquipmentStats(
                "%pump%",
                EquipmentCategory.PRODUCTION_EQUIPMENT,
                targetDepartmentId,
                targetEquipmentTypeId,
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_REPAIR,
                EquipmentStatus.DECOMMISSIONED
        );

        assertThat(stats.getTotalInRegistry()).isEqualTo(2);
        assertThat(stats.getActive()).isEqualTo(1);
        assertThat(stats.getInRepair()).isEqualTo(1);
        assertThat(stats.getDecommissioned()).isZero();
    }

    private Equipment equipment(
            String code,
            String name,
            String inventoryNumber,
            EquipmentStatus status,
            EquipmentCategory category,
            UUID departmentId,
            UUID equipmentTypeId
    ) {
        Equipment equipment = new Equipment();
        equipment.setId(UUID.randomUUID());
        equipment.setCode(code);
        equipment.setName(name);
        equipment.setInventoryNumber(inventoryNumber);
        equipment.setStatus(status);
        equipment.setCategory(category);
        equipment.setDepartmentId(departmentId);
        equipment.setEquipmentTypeId(equipmentTypeId);
        return equipment;
    }
}