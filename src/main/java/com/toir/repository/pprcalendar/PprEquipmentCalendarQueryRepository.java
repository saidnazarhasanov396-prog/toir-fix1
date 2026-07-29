package com.toir.repository.pprcalendar;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarFilter;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarOccurrenceSourceType;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bounded read model for the equipment calendar. Equipment IDs are paged before
 * metadata and occurrences are fetched for that page.
 */
public interface PprEquipmentCalendarQueryRepository {

    EquipmentIdPage findEquipmentPage(CalendarQuery query);

    List<CalendarEquipment> findEquipmentMetadata(List<UUID> equipmentIds);

    List<CalendarOccurrence> findOccurrences(CalendarQuery query, List<UUID> equipmentIds);

    CalendarDiagnostics findDiagnostics(CalendarQuery query);

    ApprovalStatus findLatestApprovalStatus(UUID planId);

    record CalendarQuery(
            UUID planId,
            UUID planDepartmentId,
            UUID scopeDepartmentId,
            LocalDate planStartDate,
            LocalDate planEndDate,
            PprEquipmentCalendarAuthoritativeSource authoritativeSource,
            Long sourceRevision,
            PprEquipmentCalendarFilter filter
    ) {
    }

    record EquipmentIdPage(List<UUID> equipmentIds, long totalElements) {
        public EquipmentIdPage {
            equipmentIds = equipmentIds == null ? List.of() : List.copyOf(equipmentIds);
        }
    }

    record CalendarEquipment(
            UUID id,
            String code,
            String name,
            String inventoryNumber,
            String technicalNumber,
            EquipmentStatus status,
            boolean deleted,
            String criticalityCode,
            UUID physicalDepartmentId,
            String physicalDepartmentCode,
            String physicalDepartmentName,
            UUID responsibleDepartmentId,
            String responsibleDepartmentCode,
            String responsibleDepartmentName,
            UUID parentId,
            String parentCode,
            String parentName,
            UUID locationId,
            String locationCode,
            String locationName,
            UUID equipmentTypeId,
            String equipmentTypeCode,
            String equipmentTypeName
    ) {
    }

    record CalendarOccurrence(
            UUID equipmentId,
            PprEquipmentCalendarOccurrenceSourceType sourceType,
            UUID taskId,
            UUID calculationItemId,
            UUID regulationId,
            UUID maintenanceRuleId,
            String sourceCode,
            String sourceName,
            MaintenanceKind maintenanceKind,
            LocalDate plannedDate,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            LocalDateTime dueDate,
            PprTaskStatus status,
            PriorityLevel priority,
            BigDecimal plannedLaborHours,
            String title
    ) {
    }

    record CalendarDiagnostics(
            long missingEquipment,
            long unresolvedEquipment,
            long outsidePlanYear,
            long outsidePlanRange
    ) {
    }
}
