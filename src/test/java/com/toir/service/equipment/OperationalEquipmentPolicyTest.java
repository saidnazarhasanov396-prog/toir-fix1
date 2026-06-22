package com.toir.service.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCommissioningStatus;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.equipment.EquipmentCommissioningActRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalEquipmentPolicyTest {

    private final EquipmentCommissioningActRepository repository =
            mock(EquipmentCommissioningActRepository.class);
    private final OperationalEquipmentPolicy policy = new OperationalEquipmentPolicy(repository);

    @Test
    void requiresActiveDepartmentOperationStartAndApprovedAct() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCurrentLocationType(EquipmentLocationType.DEPARTMENT);
        equipment.setOperationStartDate(LocalDate.now());
        when(repository.existsByEquipmentIdAndStatusAndIsDeletedFalse(
                equipmentId, EquipmentCommissioningStatus.APPROVED)).thenReturn(true);

        assertThat(policy.isOperational(equipment)).isTrue();

        equipment.setOperationStartDate(null);
        assertThat(policy.isOperational(equipment)).isFalse();
    }
}
