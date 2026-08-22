package com.toir.service.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleOperation;
import com.toir.exception.RestException;
import com.toir.exception.SparePartLifecycleErrorCodes;
import com.toir.repository.sparepartlifecycle.SparePartDueEventRepository;
import com.toir.security.ScopeAccessService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SparePartLifecycleOperationGuard {

    private final SparePartDueEventRepository dueEventRepository;
    private final ScopeAccessService scopeAccessService;

    public void assertAllowed(UUID equipmentId, SparePartLifecycleOperation operation) {
        if (equipmentId == null) {
            return;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        List<SparePartDueEvent> blocking = dueEventRepository.findBlockingForEquipmentForUpdate(equipmentId);
        if (blocking.isEmpty()) {
            return;
        }
        String eventIds = blocking.stream().map(event -> event.getId().toString()).sorted().toList().toString();
        throw RestException.conflict(
                "Spare-part lifecycle blocks " + operation + " for equipment " + equipmentId
                        + "; blockingEventCount=" + blocking.size() + "; blockingEventIds=" + eventIds,
                SparePartLifecycleErrorCodes.LIFECYCLE_BLOCKED);
    }
}
