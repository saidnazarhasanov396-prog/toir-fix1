package com.toir.service;

import com.toir.entity.RcmSnapshot;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.CriticalityClassRepository;
import com.toir.repository.RcmSnapshotRepository;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RcmServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    CriticalityClassRepository criticalityClassRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    ReliabilityMetricRepository reliabilityMetricRepository;

    @Mock
    RcmSnapshotRepository snapshotRepository;

    @InjectMocks
    RcmService service;

    @Test
    void getHistoryUnknownEquipmentReturns404() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.historyFor(equipmentId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment not found");
    }

    @Test
    void getHistoryKnownEquipmentWithoutSnapshotsReturnsEmptyArray() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(snapshotRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByCapturedAtDesc(equipmentId))
                .thenReturn(List.of());

        List<RcmSnapshot> history = service.historyFor(equipmentId);

        assertThat(history).isNotNull().isEmpty();
    }

    @Test
    void postSnapshotGeneratesAndSavesSnapshots() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment(equipmentId)));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(snapshotRepository.save(any(RcmSnapshot.class))).thenAnswer(invocation -> {
            RcmSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(UUID.randomUUID());
            return snapshot;
        });

        List<RcmSnapshot> created = service.captureSnapshot();

        assertThat(created).hasSize(1);
        ArgumentCaptor<RcmSnapshot> captor = ArgumentCaptor.forClass(RcmSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getEquipmentId()).isEqualTo(equipmentId);
    }

    @Test
    void postThenGetHistoryReturnsNonEmptyForSameEquipment() {
        UUID equipmentId = UUID.randomUUID();
        List<RcmSnapshot> storage = new ArrayList<>();

        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment(equipmentId)));
        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(snapshotRepository.save(any(RcmSnapshot.class))).thenAnswer(invocation -> {
            RcmSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(UUID.randomUUID());
            storage.add(snapshot);
            return snapshot;
        });
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(snapshotRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByCapturedAtDesc(equipmentId))
                .thenAnswer(invocation -> storage.stream()
                        .filter(s -> equipmentId.equals(s.getEquipmentId()))
                        .toList());

        service.captureSnapshot();
        List<RcmSnapshot> history = service.historyFor(equipmentId);

        assertThat(history).isNotEmpty();
        assertThat(history.getFirst().getEquipmentId()).isEqualTo(equipmentId);
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-1001");
        equipment.setName("Compressor");
        equipment.setInventoryNumber("INV-1001");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }
}
