package com.toir.dto.equipment;

import com.toir.enums.DefectStatus;
import com.toir.enums.DowntimeType;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeDto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EquipmentDetailDto(
        EquipmentDto equipment,
        List<RepairRequestShortDto> repairRequests,
        List<DefectShortDto> defects,
        List<WorkOrderShortDto> workOrders,
        List<DowntimeEventShortDto> downtimeEvents,
        List<EquipmentAttributeValueDto> attributes,
        List<EquipmentManualAttributeDto> manualAttributes,
        List<EquipmentDocumentDto> documents
) {
    public EquipmentDetailDto(
            EquipmentDto equipment,
            List<RepairRequestShortDto> repairRequests,
            List<DefectShortDto> defects,
            List<WorkOrderShortDto> workOrders,
            List<DowntimeEventShortDto> downtimeEvents,
            List<EquipmentAttributeValueDto> attributes
    ) {
        this(equipment, repairRequests, defects, workOrders, downtimeEvents, attributes, List.of(), List.of());
    }

    public EquipmentDetailDto(
            EquipmentDto equipment,
            List<RepairRequestShortDto> repairRequests,
            List<DefectShortDto> defects,
            List<WorkOrderShortDto> workOrders,
            List<DowntimeEventShortDto> downtimeEvents,
            List<EquipmentAttributeValueDto> attributes,
            List<EquipmentManualAttributeDto> manualAttributes
    ) {
        this(equipment, repairRequests, defects, workOrders, downtimeEvents, attributes, manualAttributes, List.of());
    }

    public EquipmentDetailDto(
            EquipmentDto equipment,
            List<RepairRequestShortDto> repairRequests,
            List<DefectShortDto> defects,
            List<WorkOrderShortDto> workOrders,
            List<DowntimeEventShortDto> downtimeEvents
    ) {
        this(equipment, repairRequests, defects, workOrders, downtimeEvents, List.of(), List.of(), List.of());
    }

    public EquipmentDetailDto {
        equipment = Objects.requireNonNull(equipment, "equipment is required");
        repairRequests = repairRequests == null ? List.of() : List.copyOf(repairRequests);
        defects = defects == null ? List.of() : List.copyOf(defects);
        workOrders = workOrders == null ? List.of() : List.copyOf(workOrders);
        downtimeEvents = downtimeEvents == null ? List.of() : List.copyOf(downtimeEvents);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        manualAttributes = manualAttributes == null ? List.of() : List.copyOf(manualAttributes);
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public record RepairRequestShortDto(
            UUID id,
            String number,
            String title,
            RequestStatus status,
            Instant detectedAt,
            String description
    ) {
    }

    public record DefectShortDto(
            UUID id,
            String code,
            String title,
            DefectStatus status,
            Instant detectedAt,
            String description
    ) {
    }

    public record WorkOrderShortDto(
            UUID id,
            String number,
            String title,
            WorkOrderStatus status,
            Instant startedAt,
            Instant completedAt,
            String summary
    ) {
    }

    public record DowntimeEventShortDto(
            UUID id,
            Instant startAt,
            Instant endAt,
            Integer durationMinutes,
            DowntimeType type,
            String description
    ) {
    }
}
