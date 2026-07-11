package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.PlannedShutdownWorkItemRequest;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.PlannedShutdownWorkItemSourceType;
import com.toir.exception.RestException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class PlannedShutdownWorkItemPolicy {
    public void validate(PlannedShutdownWorkItemRequest request) {
        if (request.sourceType() == null || request.equipmentId() == null || request.priority() == null
                || request.requiresShutdown() == null || request.requiresIsolation() == null) {
            throw RestException.badRequest("Source type, equipment, priority, and safety flags are required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw RestException.badRequest("Work item requires a title");
        }
        if (request.title().trim().length() > 500) {
            throw RestException.badRequest("Work item title must not exceed 500 characters");
        }
        if (request.sourceType() == PlannedShutdownWorkItemSourceType.MANUAL) {
            if (request.sourceId() != null) throw RestException.badRequest("MANUAL work item must not have sourceId");
        } else if (request.sourceId() == null) {
            throw RestException.badRequest(request.sourceType() + " work item requires sourceId");
        }
        if (request.sourceType() == PlannedShutdownWorkItemSourceType.REPAIR_CAMPAIGN) {
            throw RestException.badRequest("REPAIR_CAMPAIGN work items are not supported until Stage 3");
        }
        if (request.orderNumber() == null || request.orderNumber() < 0) {
            throw RestException.badRequest("Work item order number must be non-negative");
        }
        if (request.plannedDurationMinutes() != null && request.plannedDurationMinutes() <= 0) {
            throw RestException.badRequest("Planned duration must be positive");
        }
    }

    public void requireIdentityMutable(PlannedShutdownStatus status,
            PlannedShutdownWorkItemSourceType oldType, UUID oldId,
            PlannedShutdownWorkItemSourceType newType, UUID newId) {
        if (status.ordinal() >= PlannedShutdownStatus.APPROVED.ordinal()
                && (oldType != newType || !Objects.equals(oldId, newId))) {
            throw RestException.conflict("Work item source identity is immutable after approval");
        }
    }
}
