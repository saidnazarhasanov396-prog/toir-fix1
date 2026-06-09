package com.toir.service;

import com.toir.repository.WorkOrderRepository;
import java.time.Year;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderNumberServiceTest {

    @Mock
    WorkOrderRepository repository;

    @InjectMocks
    WorkOrderNumberService service;

    @Test
    void nextAutoNumberUsesAutoPrefixAndIncrementsSequence() {
        String prefix = "WO-AUTO-" + Year.now().getValue() + "-";
        when(repository.maxSequenceByNumberPrefix(prefix)).thenReturn(7L);
        when(repository.existsByNumberAndIsDeletedFalse(prefix + "0008")).thenReturn(false);

        assertThat(service.nextAutoNumber()).isEqualTo(prefix + "0008");
    }

    @Test
    void nextPprNumberUsesPprPrefix() {
        String prefix = "WO-PPR-" + Year.now().getValue() + "-";
        when(repository.maxSequenceByNumberPrefix(prefix)).thenReturn(0L);
        when(repository.existsByNumberAndIsDeletedFalse(prefix + "0001")).thenReturn(false);

        assertThat(service.nextPprNumber()).isEqualTo(prefix + "0001");
    }

    @Test
    void nextPprNumberSkipsReservedNumbers() {
        String prefix = "WO-PPR-" + Year.now().getValue() + "-";
        when(repository.maxSequenceByNumberPrefix(prefix)).thenReturn(0L);
        when(repository.existsByNumberAndIsDeletedFalse(prefix + "0002")).thenReturn(false);

        assertThat(service.nextPprNumber(Set.of(prefix + "0001"))).isEqualTo(prefix + "0002");
    }

    @Test
    void nextManualNumberUsesManualPrefixAndAvoidsCollision() {
        String prefix = "WO-MANUAL-" + Year.now().getValue() + "-";
        when(repository.maxSequenceByNumberPrefix(prefix)).thenReturn(1L);
        when(repository.existsByNumberAndIsDeletedFalse(prefix + "0002")).thenReturn(true);
        when(repository.existsByNumberAndIsDeletedFalse(prefix + "0003")).thenReturn(false);

        assertThat(service.nextManualNumber()).isEqualTo(prefix + "0003");
    }
}
