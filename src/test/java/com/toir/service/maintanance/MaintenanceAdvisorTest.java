package com.toir.service.maintanance;

import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.entity.ConditionReading;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.CalibrationRecord;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.ConditionParameter;
import com.toir.enums.DefectStatus;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.RcmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceAdvisorTest {

    @Mock
    private RcmService rcmService;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private DefectRepository defectRepository;
    @Mock
    private ConditionReadingRepository conditionReadingRepository;
    @Mock
    private CalibrationRecordRepository calibrationRecordRepository;

    @InjectMocks
    private MaintenanceAdvisor advisor;

    @Test
    void adviceReturnsStructuredActionsForEveryBackendCondition() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("EQ-1");
        equipment.setName("Pump");

        EquipmentRiskScore riskScore = new EquipmentRiskScore(
                equipmentId,
                "EQ-1",
                "Pump",
                null,
                null,
                20,
                4,
                80,
                null,
                3,
                0,
                0
        );

        Defect openDefect1 = defect(equipmentId, DefectStatus.OPEN);
        Defect openDefect2 = defect(equipmentId, DefectStatus.IN_PROGRESS);
        Defect openDefect3 = defect(equipmentId, DefectStatus.OPEN);

        ConditionReading alarm = new ConditionReading();
        alarm.setEquipmentId(equipmentId);
        alarm.setParameter(ConditionParameter.TEMPERATURE);
        alarm.setValue(95.0);
        alarm.setSeverity("ALARM");

        CalibrationRecord calibration = new CalibrationRecord();
        calibration.setEquipmentId(equipmentId);
        calibration.setPerformedAt(LocalDate.now().minusMonths(12));
        calibration.setNextDueAt(LocalDate.now().minusDays(2));

        when(rcmService.computeAll()).thenReturn(List.of(riskScore));
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(openDefect1, openDefect2, openDefect3));
        when(conditionReadingRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByRecordedAtDesc(equipmentId))
                .thenReturn(List.of(alarm));
        when(calibrationRecordRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByPerformedAtDesc(equipmentId))
                .thenReturn(List.of(calibration));

        MaintenanceAdvisor.EquipmentAdvice advice = advisor.advice(null, null).getFirst();

        assertEquals(4, advice.actionItems().size());
        assertTrue(advice.actionItems().stream().anyMatch((action) ->
                "SCHEDULE_INSPECTION".equals(action.type())
                        && "/inspection".equals(action.targetPath())
                        && equipmentId.toString().equals(action.query().get("equipmentId"))
                        && "create-route".equals(action.query().get("mode"))));
        assertTrue(advice.actionItems().stream().anyMatch((action) ->
                "OPEN_DEFECTS".equals(action.type())
                        && "/defects".equals(action.targetPath())
                        && "open".equals(action.query().get("statusScope"))));
        assertTrue(advice.actionItems().stream().anyMatch((action) ->
                "OPEN_MONITORING".equals(action.type())
                        && "/meters".equals(action.targetPath())));
        assertTrue(advice.actionItems().stream().anyMatch((action) ->
                "SCHEDULE_CALIBRATION".equals(action.type())
                        && "/calibrations".equals(action.targetPath())
                        && "create".equals(action.query().get("mode"))));
    }

    private Defect defect(UUID equipmentId, DefectStatus status) {
        Defect defect = new Defect();
        defect.setEquipmentId(equipmentId);
        defect.setStatus(status);
        return defect;
    }
}
