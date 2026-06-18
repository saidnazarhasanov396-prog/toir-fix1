package com.toir.dto.procurement;

import com.toir.entity.SparePart;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProcurementRequestDto(
        UUID id,
        String number,
        String title,
        String description,
        UUID departmentId,
        UUID warehouseId,
        String departmentName,
        String warehouseName,
        UUID requestedBy,
        UUID approvedBy,
        ProcurementRequestType type,
        UUID sourceDefectId,
        String sourceDefectTitle,
        UUID sourcePprTaskId,
        String sourcePprTaskTitle,
        ProcurementRequestStatus status,
        String source,
        LocalDate requiredBy,
        double totalEstimatedCost,
        Instant submittedAt,
        Instant approvedAt,
        Instant orderedAt,
        Instant receivedAt,
        String rejectionReason,
        List<ProcurementRequestLineDto> lines
) {
    public ProcurementRequestDto(UUID id,
                                 String number,
                                 String title,
                                 String description,
                                 UUID departmentId,
                                 UUID warehouseId,
                                 UUID requestedBy,
                                 UUID approvedBy,
                                 ProcurementRequestStatus status,
                                 String source,
                                 LocalDate requiredBy,
                                 double totalEstimatedCost,
                                 Instant submittedAt,
                                 Instant approvedAt,
                                 Instant orderedAt,
                                 Instant receivedAt,
                                 String rejectionReason,
                                 List<ProcurementRequestLineDto> lines) {
        this(id, number, title, description, departmentId, warehouseId, null, null, requestedBy,
                approvedBy, ProcurementRequestType.SPARE_PART, null, null, null, null, status, source,
                requiredBy, totalEstimatedCost, submittedAt, approvedAt, orderedAt, receivedAt,
                rejectionReason, lines);
    }

    public static ProcurementRequestDto from(ProcurementRequest r) {
        return from(r, null, null, Map.of());
    }

    public static ProcurementRequestDto from(ProcurementRequest r, Map<UUID, SparePart> sparePartsById) {
        return from(r, null, null, sparePartsById);
    }

    public static ProcurementRequestDto from(ProcurementRequest r,
                                             String departmentName,
                                             String warehouseName,
                                             Map<UUID, SparePart> sparePartsById) {
        Map<UUID, SparePart> safeSparePartsById = sparePartsById == null ? Map.of() : sparePartsById;
        return new ProcurementRequestDto(
                r.getId(),
                r.getNumber(),
                r.getTitle(),
                r.getDescription(),
                r.getDepartmentId(),
                r.getWarehouseId(),
                departmentName,
                warehouseName,
                r.getRequestedBy(),
                r.getApprovedBy(),
                r.getType() == null ? ProcurementRequestType.SPARE_PART : r.getType(),
                r.getSourceDefectId(),
                r.getSourceDefectTitle(),
                r.getSourcePprTaskId(),
                r.getSourcePprTaskTitle(),
                r.getStatus(),
                r.getSource(),
                r.getRequiredBy(),
                r.getTotalEstimatedCost(),
                r.getSubmittedAt(),
                r.getApprovedAt(),
                r.getOrderedAt(),
                r.getReceivedAt(),
                r.getRejectionReason(),
                r.getLines() == null ? List.of() : r.getLines().stream()
                        .map(line -> ProcurementRequestLineDto.from(
                                line,
                                line.getSparePartId() == null ? null : safeSparePartsById.get(line.getSparePartId())
                        ))
                        .toList()
        );
    }
}
