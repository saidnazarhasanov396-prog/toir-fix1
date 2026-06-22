package com.toir.service.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCommissioningStatus;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.equipment.EquipmentCommissioningActRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationalEquipmentPolicy {

    private final EquipmentCommissioningActRepository commissioningActRepository;

    public boolean isOperational(Equipment equipment) {
        return equipment != null
                && equipment.getStatus() == EquipmentStatus.ACTIVE
                && equipment.getCurrentLocationType() == EquipmentLocationType.DEPARTMENT
                && equipment.getOperationStartDate() != null
                && commissioningActRepository.existsByEquipmentIdAndStatusAndIsDeletedFalse(
                        equipment.getId(), EquipmentCommissioningStatus.APPROVED);
    }
}
