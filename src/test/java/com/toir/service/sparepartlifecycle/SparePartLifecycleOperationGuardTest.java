package com.toir.service.sparepartlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleOperation;
import com.toir.exception.RestException;
import com.toir.exception.SparePartLifecycleErrorCodes;
import com.toir.repository.sparepartlifecycle.SparePartDueEventRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SparePartLifecycleOperationGuardTest {

    @Mock
    SparePartDueEventRepository repository;

    @InjectMocks
    SparePartLifecycleOperationGuard guard;

    @Test
    void blockedEventRejectsOperationWithStableMachineCode() {
        UUID equipmentId = UUID.randomUUID();
        SparePartDueEvent event = new SparePartDueEvent();
        event.setId(UUID.randomUUID());
        when(repository.findBlockingForEquipmentForUpdate(equipmentId)).thenReturn(List.of(event));

        assertThatThrownBy(() -> guard.assertAllowed(equipmentId, SparePartLifecycleOperation.WORK_ORDER_START))
                .isInstanceOfSatisfying(RestException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(SparePartLifecycleErrorCodes.LIFECYCLE_BLOCKED));
    }

    @Test
    void noBlockingEventAllowsOperation() {
        UUID equipmentId = UUID.randomUUID();
        when(repository.findBlockingForEquipmentForUpdate(equipmentId)).thenReturn(List.of());

        guard.assertAllowed(equipmentId, SparePartLifecycleOperation.MATERIAL_ISSUE);
    }
}
