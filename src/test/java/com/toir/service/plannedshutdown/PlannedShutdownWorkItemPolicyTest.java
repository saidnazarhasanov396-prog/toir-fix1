package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.PlannedShutdownWorkItemRequest;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.PlannedShutdownWorkItemSourceType;
import com.toir.enums.PriorityLevel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlannedShutdownWorkItemPolicyTest {
    private final PlannedShutdownWorkItemPolicy policy = new PlannedShutdownWorkItemPolicy();
    private final UUID equipmentId = UUID.randomUUID();

    @Test
    void manualRequiresNoSourceIdAndAUsableTitle() {
        assertThatThrownBy(() -> policy.validate(request(PlannedShutdownWorkItemSourceType.MANUAL,
                UUID.randomUUID(), "manual"))).hasMessageContaining("must not have sourceId");
        assertThatThrownBy(() -> policy.validate(request(PlannedShutdownWorkItemSourceType.MANUAL,
                null, " "))).hasMessageContaining("requires a title");
    }

    @Test
    void typedSourcesRequireIdentityAndRepairCampaignIsReservedForStageThree() {
        assertThatThrownBy(() -> policy.validate(request(PlannedShutdownWorkItemSourceType.DEFECT, null, "D")))
                .hasMessageContaining("requires sourceId");
        assertThatThrownBy(() -> policy.validate(request(PlannedShutdownWorkItemSourceType.REPAIR_CAMPAIGN,
                UUID.randomUUID(), "RC"))).hasMessageContaining("Stage 3");
        assertThatCode(() -> policy.validate(request(PlannedShutdownWorkItemSourceType.PPR,
                UUID.randomUUID(), "PPR"))).doesNotThrowAnyException();
    }

    @Test
    void sourceIdentityBecomesImmutableAfterApproval() {
        UUID oldId = UUID.randomUUID();
        assertThatThrownBy(() -> policy.requireIdentityMutable(PlannedShutdownStatus.APPROVED,
                PlannedShutdownWorkItemSourceType.DEFECT, oldId,
                PlannedShutdownWorkItemSourceType.WORK_ORDER, UUID.randomUUID()))
                .hasMessageContaining("immutable after approval");
        assertThatCode(() -> policy.requireIdentityMutable(PlannedShutdownStatus.SCOPE_FORMATION,
                PlannedShutdownWorkItemSourceType.DEFECT, oldId,
                PlannedShutdownWorkItemSourceType.WORK_ORDER, UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void priorityOrderAndDurationHaveExplicitBoundaries() {
        assertThatThrownBy(() -> policy.validate(new PlannedShutdownWorkItemRequest(1L,
                PlannedShutdownWorkItemSourceType.MANUAL, null, equipmentId, "M", PriorityLevel.HIGH,
                true, false, 0, null, 0))).hasMessageContaining("duration");
        assertThatThrownBy(() -> policy.validate(new PlannedShutdownWorkItemRequest(1L,
                PlannedShutdownWorkItemSourceType.MANUAL, null, equipmentId, "M", PriorityLevel.HIGH,
                true, false, 10, null, -1))).hasMessageContaining("order");
    }

    private PlannedShutdownWorkItemRequest request(PlannedShutdownWorkItemSourceType type, UUID sourceId, String title) {
        return new PlannedShutdownWorkItemRequest(1L, type, sourceId, equipmentId, title, PriorityLevel.HIGH,
                true, false, 60, "A", 0);
    }
}
