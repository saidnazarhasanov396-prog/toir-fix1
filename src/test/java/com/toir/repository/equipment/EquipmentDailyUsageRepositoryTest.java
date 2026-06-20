package com.toir.repository.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentDailyUsage;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class EquipmentDailyUsageRepositoryTest {

    @Autowired
    EquipmentDailyUsageRepository repository;

    @Autowired
    EquipmentRepository equipmentRepository;

    @Test
    void findByEquipmentIdAndUsageDateReturnsSavedRow() {
        Equipment equipment = saveEquipment("EQ-001");
        LocalDate usageDate = LocalDate.of(2026, 6, 10);
        EquipmentDailyUsage saved = saveUsage(equipment.getId(), usageDate, 12.5);

        var found = repository.findByEquipmentIdAndUsageDateAndIsDeletedFalse(equipment.getId(), usageDate);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUsageValue()).isEqualTo(12.5);
    }

    @Test
    void uniqueConstraintOnEquipmentIdAndUsageDateIsEnforced() {
        Equipment equipment = saveEquipment("EQ-002");
        LocalDate usageDate = LocalDate.of(2026, 6, 11);
        saveUsage(equipment.getId(), usageDate, 5.0);

        EquipmentDailyUsage duplicate = new EquipmentDailyUsage();
        duplicate.setEquipmentId(equipment.getId());
        duplicate.setUsageDate(usageDate);
        duplicate.setUsageValue(7.0);
        duplicate.setDeleted(false);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAllByEquipmentIdAndUsageDateBetweenReturnsOnlyRowsInRange() {
        Equipment equipment = saveEquipment("EQ-003");
        saveUsage(equipment.getId(), LocalDate.of(2026, 6, 1), 1.0);
        saveUsage(equipment.getId(), LocalDate.of(2026, 6, 5), 2.0);
        saveUsage(equipment.getId(), LocalDate.of(2026, 6, 10), 3.0);
        saveUsage(equipment.getId(), LocalDate.of(2026, 6, 20), 4.0);

        List<EquipmentDailyUsage> result = repository.findAllByEquipmentIdAndUsageDateBetweenAndIsDeletedFalse(
                equipment.getId(), LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 15));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(EquipmentDailyUsage::getUsageValue).containsExactly(2.0, 3.0);
    }

    @Test
    void sumAllTimeUsageSumsRowsForEquipmentAndExcludesDeletedAndOtherEquipment() {
        Equipment target = saveEquipment("EQ-004");
        Equipment other = saveEquipment("EQ-005");
        saveUsage(target.getId(), LocalDate.of(2026, 6, 1), 10.0);
        saveUsage(target.getId(), LocalDate.of(2026, 6, 2), 20.0);
        saveUsage(other.getId(), LocalDate.of(2026, 6, 1), 100.0);

        EquipmentDailyUsage deleted = saveUsage(target.getId(), LocalDate.of(2026, 6, 3), 5.0);
        deleted.setDeleted(true);
        repository.save(deleted);

        double sum = repository.sumAllTimeUsage(target.getId());

        assertThat(sum).isEqualTo(30.0);
    }

    private Equipment saveEquipment(String code) {
        Equipment equipment = new Equipment();
        equipment.setId(UUID.randomUUID());
        equipment.setCode(code);
        equipment.setName("Equipment " + code);
        equipment.setInventoryNumber("INV-" + code);
        equipment.setTechnicalNumber("TN-" + code);
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        equipment.setDeleted(false);
        return equipmentRepository.save(equipment);
    }

    private EquipmentDailyUsage saveUsage(UUID equipmentId, LocalDate usageDate, double usageValue) {
        EquipmentDailyUsage usage = new EquipmentDailyUsage();
        usage.setEquipmentId(equipmentId);
        usage.setUsageDate(usageDate);
        usage.setUsageValue(usageValue);
        usage.setDeleted(false);
        return repository.save(usage);
    }
}
