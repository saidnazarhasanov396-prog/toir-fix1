package com.toir.dto.equipmentnode;

import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import com.toir.enums.EquipmentNodeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EquipmentNodeLifecycleDto(
        NodeSummary node,
        Counts counts,
        List<DefectItem> defects,
        List<WorkOrderItem> workOrders,
        List<DocumentItem> documents,
        List<TimelineItem> timeline
) {
    public EquipmentNodeLifecycleDto {
        defects = defects == null ? List.of() : List.copyOf(defects);
        workOrders = workOrders == null ? List.of() : List.copyOf(workOrders);
        documents = documents == null ? List.of() : List.copyOf(documents);
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }

    public record NodeSummary(
            UUID id,
            UUID equipmentId,
            UUID parentId,
            String code,
            String name,
            EquipmentNodeType nodeType,
            String serialNumber
    ) {
    }

    public record Counts(
            int defects,
            int workOrders,
            int documents
    ) {
    }

    public record DefectItem(
            UUID id,
            String code,
            String title,
            String status,
            String severity,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record WorkOrderItem(
            UUID id,
            String number,
            String title,
            String status,
            String type,
            String workType,
            String priority,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DocumentItem(
            UUID id,
            String title,
            String type,
            String revision,
            LocalDate documentDate,
            TechnicalDocumentDto.FileRef file,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TimelineItem(
            String type,
            UUID id,
            String title,
            String status,
            String metadata,
            Instant occurredAt,
            Instant sourceCreatedAt
    ) {
    }
}
