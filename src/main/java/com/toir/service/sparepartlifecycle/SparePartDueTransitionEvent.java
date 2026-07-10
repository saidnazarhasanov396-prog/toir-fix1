package com.toir.service.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import java.util.UUID;

public record SparePartDueTransitionEvent(
        UUID equipmentId,
        UUID installationId,
        UUID dueEventId,
        SparePartDueEventState state,
        SparePartDueAction action
) {
}
