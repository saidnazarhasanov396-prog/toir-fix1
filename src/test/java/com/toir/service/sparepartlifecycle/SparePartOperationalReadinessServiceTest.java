package com.toir.service.sparepartlifecycle;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.enums.sparepartlifecycle.SparePartOperationalReadiness;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SparePartOperationalReadinessServiceTest {

    private final SparePartOperationalReadinessService service = new SparePartOperationalReadinessService(null, null, null);

    @Test
    void evaluationErrorPrecedesBlockMaintenanceAndWarningWithoutChangingBaseStatus() {
        Equipment equipment = equipment();
        SparePartInstallation error = installation(SparePartLifecycleEvaluationState.ERROR);
        SparePartDueEvent blocker = event(error, SparePartDueAction.BLOCK_OPERATION, SparePartDueEventState.OVERDUE);

        var result = service.evaluate(equipment, List.of(error), List.of(blocker));

        assertThat(result.baseEquipmentStatus()).isEqualTo(EquipmentStatus.ACTIVE);
        assertThat(result.operationalReadiness()).isEqualTo(SparePartOperationalReadiness.EVALUATION_ERROR);
        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.ACTIVE);
    }

    @Test
    void dueActionPrecedenceIsBlockThenMaintenanceThenWarning() {
        Equipment equipment = equipment();
        SparePartInstallation installation = installation(SparePartLifecycleEvaluationState.DUE);

        assertThat(service.evaluate(equipment, List.of(installation), List.of(
                event(installation, SparePartDueAction.WARNING_ONLY, SparePartDueEventState.WARNING)
        )).operationalReadiness()).isEqualTo(SparePartOperationalReadiness.WARNING);
        assertThat(service.evaluate(equipment, List.of(installation), List.of(
                event(installation, SparePartDueAction.MAINTENANCE_REQUIRED, SparePartDueEventState.DUE)
        )).operationalReadiness()).isEqualTo(SparePartOperationalReadiness.MAINTENANCE_REQUIRED);
        assertThat(service.evaluate(equipment, List.of(installation), List.of(
                event(installation, SparePartDueAction.BLOCK_OPERATION, SparePartDueEventState.DUE),
                event(installation, SparePartDueAction.MAINTENANCE_REQUIRED, SparePartDueEventState.DUE)
        )).operationalReadiness()).isEqualTo(SparePartOperationalReadiness.BLOCKED);
    }

    @Test
    void noActivePartIssueIsReady() {
        var result = service.evaluate(equipment(), List.of(), List.of());

        assertThat(result.operationalReadiness()).isEqualTo(SparePartOperationalReadiness.READY);
        assertThat(result.reasons()).isEmpty();
    }

    private static Equipment equipment() {
        Equipment equipment = new Equipment();
        equipment.setId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        return equipment;
    }

    private static SparePartInstallation installation(SparePartLifecycleEvaluationState state) {
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(UUID.randomUUID());
        installation.setSparePartId(UUID.randomUUID());
        installation.setPositionKey("N:ROOT:S:DEFAULT");
        installation.setLifecycleEvaluationState(state);
        return installation;
    }

    private static SparePartDueEvent event(SparePartInstallation installation,
                                           SparePartDueAction action,
                                           SparePartDueEventState state) {
        SparePartDueEvent event = new SparePartDueEvent();
        event.setId(UUID.randomUUID());
        event.setInstallationId(installation.getId());
        event.setDueAction(action);
        event.setState(state);
        return event;
    }
}
