package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentStatusChangeRequest;
import com.toir.dto.equipment.EquipmentStatusHistoryResponse;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentStatusHistory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.EquipmentStatusSource;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EquipmentStatusLifecycleService {

    private static final String RELATED_WORK_ORDER = "WORK_ORDER";
    private static final Set<EquipmentStatus> WORK_ORDER_START_ALLOWED_STATUSES =
            Set.of(EquipmentStatus.ACTIVE, EquipmentStatus.STANDBY);
    private static final Set<EquipmentStatus> WORK_ORDER_PROTECTED_STATUSES =
            Set.of(EquipmentStatus.OUT_OF_SERVICE, EquipmentStatus.CONSERVATION, EquipmentStatus.DECOMMISSIONED);

    private final EquipmentRepository equipmentRepository;
    private final EquipmentStatusHistoryRepository historyRepository;

    @Transactional
    public EquipmentStatusHistoryResponse changeStatusManually(UUID equipmentId,
                                                               EquipmentStatusChangeRequest request,
                                                               UUID changedBy) {
        if (request == null || request.status() == null) {
            throw RestException.badRequest("Equipment status is required");
        }
        String reason = requireReason(request.reason());
        Equipment equipment = equipmentOrThrow(equipmentId);
        return transition(
                equipment,
                request.status(),
                reason,
                EquipmentStatusSource.MANUAL,
                changedBy,
                null,
                null,
                null
        );
    }

    @Transactional
    public EquipmentStatusHistoryResponse recordSystemTransition(UUID equipmentId,
                                                                 EquipmentStatus toStatus,
                                                                 String reason,
                                                                 String relatedEntityType,
                                                                 UUID relatedEntityId) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        return transition(
                equipment,
                toStatus,
                requireReason(reason),
                EquipmentStatusSource.SYSTEM,
                null,
                relatedEntityType,
                relatedEntityId,
                null
        );
    }

    @Transactional
    public EquipmentStatusHistoryResponse recordWorkOrderTransition(UUID workOrderId,
                                                                    UUID equipmentId,
                                                                    EquipmentStatus toStatus,
                                                                    String reason) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        if (equipment.getStatus() == EquipmentStatus.DECOMMISSIONED) {
            throw decommissioned(actionLabel("update status from work order"));
        }
        if (toStatus == EquipmentStatus.IN_REPAIR
                && !WORK_ORDER_START_ALLOWED_STATUSES.contains(equipment.getStatus())) {
            return null;
        }
        return transition(
                equipment,
                toStatus,
                requireReason(reason),
                EquipmentStatusSource.WORK_ORDER,
                null,
                RELATED_WORK_ORDER,
                workOrderId,
                null
        );
    }

    @Transactional
    public EquipmentStatusHistoryResponse recordWorkOrderReturn(UUID workOrderId,
                                                                UUID equipmentId,
                                                                String reason) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        if (WORK_ORDER_PROTECTED_STATUSES.contains(equipment.getStatus())) {
            return null;
        }
        if (equipment.getStatus() != EquipmentStatus.IN_REPAIR) {
            return null;
        }
        EquipmentStatusHistory startTransition = historyRepository
                .findTopByEquipmentIdAndSourceAndRelatedEntityTypeAndRelatedEntityIdAndIsDeletedFalseOrderByChangedAtDesc(
                        equipmentId,
                        EquipmentStatusSource.WORK_ORDER,
                        RELATED_WORK_ORDER,
                        workOrderId)
                .filter(history -> history.getToStatus() == EquipmentStatus.IN_REPAIR)
                .orElse(null);
        if (startTransition == null) {
            return null;
        }
        EquipmentStatus returnStatus = startTransition.getFromStatus() != null
                ? startTransition.getFromStatus()
                : EquipmentStatus.ACTIVE;
        return transition(
                equipment,
                returnStatus,
                requireReason(reason),
                EquipmentStatusSource.WORK_ORDER,
                null,
                RELATED_WORK_ORDER,
                workOrderId,
                "Returning equipment after work order completion"
        );
    }

    @Transactional(readOnly = true)
    public Page<EquipmentStatusHistoryResponse> getHistory(UUID equipmentId, Pageable pageable) {
        equipmentOrThrow(equipmentId);
        return historyRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByChangedAtDesc(equipmentId, pageable)
                .map(EquipmentStatusHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    public void assertOperationallyAllowed(UUID equipmentId, String action) {
        if (equipmentId == null) {
            return;
        }
        Equipment equipment = equipmentOrThrow(equipmentId);
        if (equipment.getStatus() == EquipmentStatus.DECOMMISSIONED) {
            throw decommissioned(action);
        }
    }

    private EquipmentStatusHistoryResponse transition(Equipment equipment,
                                                     EquipmentStatus toStatus,
                                                     String reason,
                                                     EquipmentStatusSource source,
                                                     UUID changedBy,
                                                     String relatedEntityType,
                                                     UUID relatedEntityId,
                                                     String note) {
        if (toStatus == null) {
            throw RestException.badRequest("Equipment status is required");
        }
        EquipmentStatus fromStatus = equipment.getStatus();
        if (source == EquipmentStatusSource.MANUAL
                && fromStatus == EquipmentStatus.STANDBY
                && toStatus == EquipmentStatus.ACTIVE) {
            throw RestException.conflict(
                    "STANDBY equipment can become ACTIVE only through equipment commissioning approval");
        }
        if (fromStatus == toStatus) {
            return recordHistory(equipment, fromStatus, toStatus, reason, source, changedBy, relatedEntityType, relatedEntityId, note);
        }
        equipment.setStatus(toStatus);
        equipmentRepository.save(equipment);
        return recordHistory(equipment, fromStatus, toStatus, reason, source, changedBy, relatedEntityType, relatedEntityId, note);
    }

    private EquipmentStatusHistoryResponse recordHistory(Equipment equipment,
                                                        EquipmentStatus fromStatus,
                                                        EquipmentStatus toStatus,
                                                        String reason,
                                                        EquipmentStatusSource source,
                                                        UUID changedBy,
                                                        String relatedEntityType,
                                                        UUID relatedEntityId,
                                                        String note) {
        EquipmentStatusHistory history = new EquipmentStatusHistory();
        history.setEquipmentId(equipment.getId());
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setReason(reason);
        history.setSource(source);
        history.setChangedBy(changedBy);
        history.setChangedAt(Instant.now());
        history.setRelatedEntityType(relatedEntityType);
        history.setRelatedEntityId(relatedEntityId);
        history.setNote(note);
        return EquipmentStatusHistoryResponse.from(historyRepository.save(history));
    }

    private Equipment equipmentOrThrow(UUID equipmentId) {
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw RestException.badRequest("Equipment status change reason is required");
        }
        return reason.trim();
    }

    private RestException decommissioned(String action) {
        return RestException.conflict("Equipment is decommissioned and cannot be used for " + actionLabel(action));
    }

    private String actionLabel(String action) {
        return action == null || action.isBlank() ? "this operation" : action;
    }
}
