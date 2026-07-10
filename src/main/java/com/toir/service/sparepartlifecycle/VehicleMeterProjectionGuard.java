package com.toir.service.sparepartlifecycle;

import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.MeterType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentMeterRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleMeterProjectionGuard {

    private final EquipmentMeterRepository meterRepository;

    @Transactional(readOnly = true)
    public void assertCompatibleUpdate(UUID equipmentId,
                                       VehicleDetails currentDetails,
                                       Double requestedOdometerKm,
                                       Double requestedEngineHours) {
        assertMetricUnchangedWhenMeterExists(
                equipmentId,
                MeterType.MILEAGE_KM,
                currentDetails.getCurrentOdometerKm(),
                requestedOdometerKm
        );
        assertMetricUnchangedWhenMeterExists(
                equipmentId,
                MeterType.ENGINE_HOURS,
                currentDetails.getCurrentEngineHours(),
                requestedEngineHours
        );
    }

    private void assertMetricUnchangedWhenMeterExists(UUID equipmentId,
                                                       MeterType meterType,
                                                       double currentValue,
                                                       Double requestedValue) {
        if (requestedValue == null || Double.compare(currentValue, requestedValue) == 0) {
            return;
        }
        if (!meterRepository.findAllActiveByEquipmentAndType(equipmentId, meterType).isEmpty()) {
            throw RestException.conflict(
                    "METER_PROJECTION_READ_ONLY: record a canonical meter reading to change " + meterType);
        }
    }
}
