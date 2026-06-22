package com.toir.service;

import com.toir.controller.ReliabilityPassportController.ReliabilityPassport;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.DefectStatus;
import com.toir.enums.DowntimeType;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReliabilityPassportServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    DowntimeEventRepository downtimeRepository;

    @InjectMocks
    ReliabilityPassportService service;

    @Test
    void passportUsesOnlyActiveDefectsAndFailureDowntime() {
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId);

        Defect open = defect(DefectStatus.OPEN, "Wear", null);
        Defect resolved = defect(DefectStatus.RESOLVED, null, "Overload");
        Defect cancelled = defect(DefectStatus.CANCELLED, "Noise", null);

        DowntimeEvent planned = downtime(now.minus(Duration.ofDays(10)), 600, DowntimeType.PLANNED);
        DowntimeEvent unplanned = downtime(now.minus(Duration.ofDays(5)), 120, DowntimeType.UNPLANNED);

        stubPassport(equipment, List.of(open, resolved, cancelled), List.of(planned, unplanned));

        ReliabilityPassport result = service.passport(equipmentId);

        assertThat(result.totalDefects()).isEqualTo(2);
        assertThat(result.openDefects()).isEqualTo(1);
        assertThat(result.totalDowntimeEvents()).isEqualTo(1);
        assertThat(result.totalDowntimeMinutes()).isEqualTo(120);
        assertThat(result.mttrHours()).isEqualTo(2.0);
        assertThat(result.mtbfHours()).isCloseTo(8758.0, withinOneMinute());
        assertThat(result.availabilityPct()).isCloseTo(8758.0 / 8760.0 * 100.0, withinOneMinute());
        assertThat(result.topRootCauses()).extracting(cause -> cause.cause())
                .containsExactlyInAnyOrder("Wear", "Overload");
    }

    @Test
    void passportClipsBoundaryEventsAndCountsOpenDowntimeThroughNow() {
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId);

        DowntimeEvent boundary = downtime(now.minus(Duration.ofDays(365)).minus(Duration.ofHours(1)),
                120, DowntimeType.UNPLANNED);
        DowntimeEvent open = DowntimeEvent.builder()
                .equipmentId(equipmentId)
                .startAt(now.minus(Duration.ofMinutes(90)))
                .type(DowntimeType.EMERGENCY)
                .build();

        stubPassport(equipment, List.of(), List.of(boundary, open));

        ReliabilityPassport result = service.passport(equipmentId);

        assertThat(result.totalDowntimeEvents()).isEqualTo(2);
        assertThat(result.totalDowntimeMinutes()).isBetween(149L, 151L);
        assertThat(result.mttrHours()).isBetween(59.0 / 60.0, 1.0);
        assertThat(result.mtbfHours()).isNotNull();
        assertThat(result.availabilityPct()).isLessThan(100.0);
    }

    @Test
    void passportReturnsNullMttrWhenFailureDowntimeIsStillOpen() {
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.now();
        Equipment equipment = equipment(equipmentId);
        DowntimeEvent open = DowntimeEvent.builder()
                .equipmentId(equipmentId)
                .startAt(now.minus(Duration.ofHours(2)))
                .type(DowntimeType.UNPLANNED)
                .build();

        stubPassport(equipment, List.of(), List.of(open));

        ReliabilityPassport result = service.passport(equipmentId);

        assertThat(result.totalDowntimeMinutes()).isBetween(119L, 121L);
        assertThat(result.mttrHours()).isNull();
        assertThat(result.mtbfHours()).isNotNull();
    }

    private void stubPassport(Equipment equipment, List<Defect> defects, List<DowntimeEvent> downtimes) {
        UUID equipmentId = equipment.getId();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(defects);
        when(downtimeRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId))
                .thenReturn(downtimes);
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = Equipment.builder()
                .code("EQ-1")
                .name("Pump")
                .inventoryNumber("INV-1")
                .build();
        equipment.setId(id);
        return equipment;
    }

    private Defect defect(DefectStatus status, String rootCause, String failureReason) {
        return Defect.builder()
                .status(status)
                .rootCause(rootCause)
                .failureReason(failureReason)
                .build();
    }

    private DowntimeEvent downtime(Instant start, int minutes, DowntimeType type) {
        return DowntimeEvent.builder()
                .startAt(start)
                .endAt(start.plus(Duration.ofMinutes(minutes)))
                .durationMinutes(minutes)
                .type(type)
                .build();
    }

    private org.assertj.core.data.Offset<Double> withinOneMinute() {
        return org.assertj.core.data.Offset.offset(1.0 / 60.0);
    }
}
