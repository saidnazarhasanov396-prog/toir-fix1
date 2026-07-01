package com.toir.repository.defect;

import com.toir.test.RepositorySliceTest;
import com.toir.entity.defects.DefectList;
import com.toir.enums.DefectListStatus;
import com.toir.repository.defects.DefectListRepository;
import com.toir.repository.projection.DefectListStatsProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
class DefectListRepositoryStatsTest {

    @Autowired
    DefectListRepository repository;

    @Test
    void getDefectListStatsWithoutFiltersCountsAllNonDeletedLists() {
        UUID equipmentId = UUID.randomUUID();

        saveDefectList("DL-001", "Pump draft", equipmentId, DefectListStatus.DRAFT);
        saveDefectList("DL-002", "Pump draft 2", equipmentId, DefectListStatus.DRAFT);
        saveDefectList("DL-003", "Pump approved", equipmentId, DefectListStatus.APPROVED);
        saveDefectList("DL-004", "Pump closed", equipmentId, DefectListStatus.CLOSED);

        DefectListStatsProjection stats = repository.getDefectListStats(
                null,
                null,
                DefectListStatus.DRAFT.name(),
                DefectListStatus.APPROVED.name(),
                DefectListStatus.CLOSED.name()
        );

        assertThat(stats.getTotalDefectLists()).isEqualTo(4);
        assertThat(stats.getDraft()).isEqualTo(2);
        assertThat(stats.getApproved()).isEqualTo(1);
        assertThat(stats.getClosed()).isEqualTo(1);
    }

    @Test
    void getDefectListStatsWithEquipmentAndSearchCountsOnlyMatchingLists() {
        UUID targetEquipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();

        saveDefectList("DL-PUMP-001", "Pump draft", targetEquipmentId, DefectListStatus.DRAFT);
        saveDefectList("DL-PUMP-002", "Pump approved", targetEquipmentId, DefectListStatus.APPROVED);

        saveDefectList("DL-PUMP-003", "Other equipment pump", otherEquipmentId, DefectListStatus.DRAFT);
        saveDefectList("DL-MOTOR-001", "Motor closed", targetEquipmentId, DefectListStatus.CLOSED);

        DefectListStatsProjection stats = repository.getDefectListStats(
                targetEquipmentId,
                "%pump%",
                DefectListStatus.DRAFT.name(),
                DefectListStatus.APPROVED.name(),
                DefectListStatus.CLOSED.name()
        );

        assertThat(stats.getTotalDefectLists()).isEqualTo(2);
        assertThat(stats.getDraft()).isEqualTo(1);
        assertThat(stats.getApproved()).isEqualTo(1);
        assertThat(stats.getClosed()).isZero();
    }

    private void saveDefectList(
            String code,
            String title,
            UUID equipmentId,
            DefectListStatus status
    ) {
        DefectList defectList = new DefectList();
        defectList.setId(UUID.randomUUID());
        defectList.setCode(code);
        defectList.setTitle(title);
        defectList.setEquipmentId(equipmentId);
        defectList.setCreatedById(UUID.randomUUID());
        defectList.setStatus(status);
        defectList.setNotes("note for " + title);
        defectList.setDeleted(false);

        repository.save(defectList);
    }
}