package com.toir.service.sparepartlifecycle;

import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.MeterType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentMeterRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CanonicalEquipmentMeterService {

    private final EquipmentMeterRepository meterRepository;

    @Transactional(readOnly = true)
    public EquipmentMeter resolve(UUID equipmentId, MeterType meterType, UUID explicitMeterId) {
        if (equipmentId == null || meterType == null) {
            throw RestException.badRequest("METER_REQUIRED: equipmentId and meterType are required");
        }
        if (explicitMeterId != null) {
            return resolveExplicit(equipmentId, meterType, explicitMeterId);
        }

        List<EquipmentMeter> primaryMeters = meterRepository.findAllActivePrimary(equipmentId, meterType);
        if (primaryMeters.size() == 1) {
            return primaryMeters.getFirst();
        }
        if (primaryMeters.size() > 1) {
            throw RestException.conflict("METER_AMBIGUOUS: multiple active primary meters match the equipment and type");
        }

        List<EquipmentMeter> activeMeters = meterRepository.findAllActiveByEquipmentAndType(equipmentId, meterType);
        if (activeMeters.isEmpty()) {
            throw RestException.conflict("METER_REQUIRED: no active meter matches the equipment and type");
        }
        if (activeMeters.size() > 1) {
            throw RestException.conflict("METER_AMBIGUOUS: select an explicit meter or designate one active primary meter");
        }
        return activeMeters.getFirst();
    }

    @Transactional(readOnly = true)
    public BigDecimal currentValue(UUID equipmentId, MeterType meterType, UUID explicitMeterId) {
        return BigDecimal.valueOf(resolve(equipmentId, meterType, explicitMeterId).getCurrentValue());
    }

    private EquipmentMeter resolveExplicit(UUID equipmentId, MeterType meterType, UUID explicitMeterId) {
        EquipmentMeter meter = meterRepository.findByIdAndIsDeletedFalse(explicitMeterId)
                .orElseThrow(() -> RestException.notFound("METER_REQUIRED: meter not found: " + explicitMeterId));
        if (!meter.isActive()) {
            throw RestException.conflict("METER_INACTIVE: meter is not active: " + explicitMeterId);
        }
        if (!Objects.equals(equipmentId, meter.getEquipmentId()) || meterType != meter.getMeterType()) {
            throw RestException.conflict("METER_MISMATCH: meter does not match the requested equipment and type");
        }
        return meter;
    }
}
