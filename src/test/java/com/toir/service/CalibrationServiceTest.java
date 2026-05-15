package com.toir.service;

import com.toir.dto.calibration.CalibrationRecordDto;
import com.toir.dto.calibration.CalibrationRecordRequest;
import com.toir.entity.equipment.CalibrationRecord;
import com.toir.exception.RestException;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalibrationServiceTest {

    @Mock
    CalibrationRecordRepository repo;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    CalibrationService service;

    @Test
    void findForEquipmentValidatesEquipmentExists() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.existsByIdAndIsDeletedFalse(equipmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.findForEquipment(equipmentId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Equipment not found");

        verify(repo, never()).findAllByEquipmentIdAndIsDeletedFalseOrderByPerformedAtDesc(equipmentId);
    }

    @Test
    void createRejectsInvalidCertificateNumber() {
        UUID equipmentId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(request(equipmentId, "bir")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("certificateNumber");
                });

        verify(repo, never()).save(any(CalibrationRecord.class));
        verify(equipmentRepository, never()).existsByIdAndIsDeletedFalse(any(UUID.class));
    }

    @Test
    void updateRejectsInvalidCertificateNumber() {
        UUID equipmentId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        CalibrationRecord existing = new CalibrationRecord();
        existing.setId(recordId);
        existing.setEquipmentId(equipmentId);
        existing.setPerformedAt(LocalDate.of(2026, 5, 1));
        when(repo.findByIdAndIsDeletedFalse(recordId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(recordId, request(equipmentId, "1")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("certificateNumber");
                });

        verify(repo).findByIdAndIsDeletedFalse(recordId);
        verify(repo, never()).save(any(CalibrationRecord.class));
        verify(equipmentRepository, never()).existsByIdAndIsDeletedFalse(any(UUID.class));
    }

    @Test
    void existingFindAllSearchBehaviorStillWorks() {
        CalibrationRecord record = new CalibrationRecord();
        record.setId(UUID.randomUUID());
        record.setEquipmentId(UUID.randomUUID());
        record.setCertificateNumber("CERT-2026-001");
        record.setPerformedBy("Lab");
        record.setPerformedAt(LocalDate.of(2026, 5, 1));
        when(repo.findAll("cert")).thenReturn(List.of(record));

        List<CalibrationRecordDto> result = service.findAll("cert");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().certificateNumber()).isEqualTo("CERT-2026-001");
        verify(repo).findAll("cert");
    }

    private CalibrationRecordRequest request(UUID equipmentId, String certificateNumber) {
        return new CalibrationRecordRequest(
                equipmentId,
                certificateNumber,
                "Lab",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 11, 1),
                "PASS",
                null,
                null,
                "%",
                null,
                null
        );
    }
}
