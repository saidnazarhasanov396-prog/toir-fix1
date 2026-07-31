package com.toir.service;

import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.dto.rcm.autoplan.RcmAutoPlanDecision;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RcmAutoPlanPreviewServiceTest {
    @Mock RcmService rcmService;
    @Mock EquipmentRepository equipmentRepository;
    @Mock MaintenanceRegulationRepository regulationRepository;
    @Mock PprPlanRepository planRepository;
    @Mock PprTaskRepository taskRepository;

    RcmAutoPlanPreviewService service;

    @BeforeEach
    void setUp() {
        service = new RcmAutoPlanPreviewService(rcmService, equipmentRepository,
                regulationRepository, planRepository, taskRepository);
    }

    @Test
    void classifiesCreateDuplicateAndAmbiguousRegulationWithoutWrites() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = new PprPlan();
        plan.setId(planId);
        plan.setName("July plan");
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));

        Equipment createEquipment = equipment("EQ-1");
        Equipment duplicateEquipment = equipment("EQ-2");
        Equipment conflictEquipment = equipment("EQ-3");
        when(rcmService.computeAll()).thenReturn(List.of(
                score(createEquipment, 75), score(duplicateEquipment, 65), score(conflictEquipment, 55)));
        when(equipmentRepository.findByIdAndIsDeletedFalse(createEquipment.getId())).thenReturn(Optional.of(createEquipment));
        when(equipmentRepository.findByIdAndIsDeletedFalse(duplicateEquipment.getId())).thenReturn(Optional.of(duplicateEquipment));
        when(equipmentRepository.findByIdAndIsDeletedFalse(conflictEquipment.getId())).thenReturn(Optional.of(conflictEquipment));

        MaintenanceRegulation createRegulation = regulation(createEquipment.getEquipmentTypeId(), "REG-1");
        MaintenanceRegulation duplicateRegulation = regulation(duplicateEquipment.getEquipmentTypeId(), "REG-2");
        when(regulationRepository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(createEquipment.getEquipmentTypeId()))
                .thenReturn(List.of(createRegulation));
        when(regulationRepository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(duplicateEquipment.getEquipmentTypeId()))
                .thenReturn(List.of(duplicateRegulation));
        when(regulationRepository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(conflictEquipment.getEquipmentTypeId()))
                .thenReturn(List.of(regulation(conflictEquipment.getEquipmentTypeId(), "REG-3A"),
                        regulation(conflictEquipment.getEquipmentTypeId(), "REG-3B")));

        PprTask existing = new PprTask();
        existing.setId(UUID.randomUUID());
        existing.setCode("RCM-EXISTING");
        existing.setSourceType("RCM_AUTO_PLAN");
        existing.setSourceKey("RCM:" + planId + ":" + duplicateEquipment.getId() + ":" + duplicateRegulation.getId());
        when(taskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(existing));

        var preview = service.preview(30, planId);

        assertThat(preview.candidates()).isEqualTo(3);
        assertThat(preview.tasksToCreate()).isEqualTo(1);
        assertThat(preview.duplicates()).isEqualTo(1);
        assertThat(preview.conflicts()).isEqualTo(1);
        assertThat(preview.rows()).extracting(row -> row.decision())
                .containsExactly(RcmAutoPlanDecision.CREATE, RcmAutoPlanDecision.DUPLICATE,
                        RcmAutoPlanDecision.CONFLICT);
        assertThat(preview.rows().get(2).conflictCodes()).containsExactly("AMBIGUOUS_REGULATION");
        assertThat(preview.fingerprint()).matches("[0-9a-f]{64}");
        verify(taskRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static Equipment equipment(String code) {
        Equipment equipment = new Equipment();
        equipment.setId(UUID.randomUUID());
        equipment.setCode(code);
        equipment.setName("Equipment " + code);
        equipment.setEquipmentTypeId(UUID.randomUUID());
        return equipment;
    }

    private static MaintenanceRegulation regulation(UUID typeId, String code) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(UUID.randomUUID());
        regulation.setCode(code);
        regulation.setName("Regulation " + code);
        regulation.setEquipmentTypeId(typeId);
        regulation.setNormativeLaborHours(4);
        return regulation;
    }

    private static EquipmentRiskScore score(Equipment equipment, int risk) {
        return new EquipmentRiskScore(equipment.getId(), equipment.getCode(), equipment.getName(),
                "A", "A", 5, 5, risk, 1, 1, 10, 2);
    }
}
