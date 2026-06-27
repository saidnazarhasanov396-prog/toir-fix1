package com.toir.service.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.NotificationSeverity;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.PermissionConstants;
import com.toir.service.NotificationService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarrantyExpiryService {

    private final EquipmentRepository equipmentRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public int notifyExpiringWarranties() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate in30 = today.plusDays(30);
        LocalDate in7 = today.plusDays(7);

        List<Equipment> expiring = equipmentRepository.findWarrantyExpiringOn(List.of(in30, in7));

        int count = 0;
        for (Equipment equipment : expiring) {
            LocalDate effectiveEnd = equipment.getWarrantyEndDate() != null
                    ? equipment.getWarrantyEndDate()
                    : equipment.getWarrantyUntil();
            if (effectiveEnd == null) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, effectiveEnd);

            String title = "Kafolat tugayapti: " + equipment.getName();
            String body = equipment.getName() + " (" + equipment.getCode() + ") jihozining kafolati "
                    + daysLeft + " kun ichida tugaydi (" + effectiveEnd + ").";

            try {
                count += notificationService.notifyDepartmentByPermission(
                        equipment.getResponsibleDepartmentId() != null
                                ? equipment.getResponsibleDepartmentId()
                                : equipment.getDepartmentId(),
                        PermissionConstants.EQUIPMENT_READ,
                        title,
                        body,
                        NotificationSeverity.INFO,
                        "WARRANTY_EXPIRY",
                        equipment.getId().toString()
                ).size();
            } catch (Exception e) {
                log.warn("Failed to send warranty expiry notification for equipment {}: {}",
                        equipment.getId(), e.getMessage());
            }
        }
        return count;
    }
}
