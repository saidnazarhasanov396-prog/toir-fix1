package com.toir.repository.equipment;

import com.toir.test.RepositorySliceTest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
class EquipmentTypeRepositoryStatsTest {

    @Autowired
    EquipmentTypeRepository equipmentTypeRepository;

    @Autowired
    EquipmentRepository equipmentRepository;

    @Test
    void getEquipmentTypeStatsWithNoFiltersCountsAll() {
        saveType("PUMP", "Pump", "ROTATING");
        saveType("MOTOR", "Motor", "ROTATING");
        saveType("VALVE", "Valve", "STATIC");

        EquipmentTypeStatsProjection stats = equipmentTypeRepository.getEquipmentTypeStats(null, null);

        assertThat(stats.getTotalTypes()).isEqualTo(3);
        assertThat(stats.getActiveCategories()).isEqualTo(2); // ROTATING, STATIC
    }

    @Test
    void getEquipmentTypeStatsCountsWithActiveEquipment() {
        EquipmentType typeWithEquip = saveType("PUMP-WE", "Pump w/equip", "ROTATING");
        EquipmentType typeNoEquip = saveType("VALVE-NE", "Valve no equip", "STATIC");

        saveEquipment(typeWithEquip.getId(), EquipmentStatus.ACTIVE);

        EquipmentTypeStatsProjection stats = equipmentTypeRepository.getEquipmentTypeStats(null, null);

        assertThat(stats.getWithActiveEquipment()).isEqualTo(1);
    }

    @Test
    void getEquipmentTypeStatsWithCategoryFilterCountsOnlyThatCategory() {
        saveType("PUMP-CF", "Pump category", "ROTATING");
        saveType("VALVE-CF", "Valve category", "STATIC");

        EquipmentTypeStatsProjection stats = equipmentTypeRepository.getEquipmentTypeStats("ROTATING", null);

        assertThat(stats.getTotalTypes()).isEqualTo(1);
    }

    @Test
    void getEquipmentTypeStatsExcludesDeletedTypes() {
        EquipmentType active = saveType("PUMP-DEL", "Active pump", "ROTATING");
        EquipmentType deleted = saveType("MOTOR-DEL", "Deleted motor", "ROTATING");
        deleted.setDeleted(true);
        equipmentTypeRepository.save(deleted);

        EquipmentTypeStatsProjection stats = equipmentTypeRepository.getEquipmentTypeStats(null, null);

        assertThat(stats.getTotalTypes()).isEqualTo(1);
    }

    private EquipmentType saveType(String code, String name, String category) {
        EquipmentType t = new EquipmentType();
        t.setCode(code + "-" + UUID.randomUUID());
        t.setName(name);
        t.setCategory(category);
        t.setDeleted(false);
        return equipmentTypeRepository.save(t);
    }

    private Equipment saveEquipment(UUID typeId, EquipmentStatus status) {
        Equipment e = new Equipment();
        e.setCode("EQ-" + UUID.randomUUID());
        e.setName("Equipment");
        e.setInventoryNumber("INV-" + UUID.randomUUID());
        e.setEquipmentTypeId(typeId);
        e.setStatus(status);
        e.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        e.setDeleted(false);
        return equipmentRepository.save(e);
    }
}
