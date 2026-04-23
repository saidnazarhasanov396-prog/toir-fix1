package com.toir.dto.procurement;
import com.toir.dto.procurement.ProcurementRequestLineDto;

import com.toir.entity.ProcurementRequest;
import com.toir.entity.ProcurementRequestStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProcurementRequestDto(
        UUID id,
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
        List<ProcurementRequestLineDto> lines
) {
    public static ProcurementRequestDto from(ProcurementRequest r) {
        return new ProcurementRequestDto(
                r.getId(),
                r.getNumber(),
                r.getTitle(),
                r.getDescription(),
                r.getDepartmentId(),
                r.getWarehouseId(),
                r.getRequestedBy(),
                r.getApprovedBy(),
                r.getStatus(),
                r.getSource(),
                r.getRequiredBy(),
                r.getTotalEstimatedCost(),
                r.getSubmittedAt(),
                r.getApprovedAt(),
                r.getOrderedAt(),
                r.getReceivedAt(),
                r.getRejectionReason(),
                r.getLines() == null ? List.of() : r.getLines().stream().map(ProcurementRequestLineDto::from).toList()
        );
    }
}
