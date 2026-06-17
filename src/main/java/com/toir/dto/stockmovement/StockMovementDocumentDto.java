package com.toir.dto.stockmovement;

import com.toir.dto.attachment.AttachmentGroupDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StockMovementDocumentDto(
        UUID id,
        String documentName,
        String documentType,
        String documentNumber,
        LocalDateTime createdAt,
        List<StockMovementFileDto> files
) {
    public static StockMovementDocumentDto fromAttachmentGroup(UUID movementId, AttachmentGroupDto group) {
        if (group == null) {
            return null;
        }
        return new StockMovementDocumentDto(
                group.id(),
                group.title(),
                group.documentType(),
                group.documentNumber(),
                group.createdAt(),
                group.files() == null
                        ? List.of()
                        : group.files().stream()
                        .map(file -> StockMovementFileDto.fromAttachmentFile(movementId, file))
                        .toList()
        );
    }
}
