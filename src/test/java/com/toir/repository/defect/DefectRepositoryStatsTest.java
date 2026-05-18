package com.toir.repository.defect;


import com.toir.entity.defects.Defect;
import com.toir.enums.DefectStatus;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.projection.DefectStatsProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",

        // Oldingi repository testlarda ishlagan DB config bilan bir xil bo‘lsin
        "spring.datasource.url=jdbc:postgresql://localhost:5433/toir",
        "spring.datasource.username=postgres",
        "spring.datasource.password=root123",
        "spring.datasource.hikari.connection-timeout=3000"
})
class DefectRepositoryStatsTest {

    @Autowired
    DefectRepository repository;

    @Test
    void getDefectStatsWithoutFiltersCountsAllNonDeletedDefects() {
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();

        saveDefect(
                "DEF-001",
                "Pump leak",
                "Pump has oil leak",
                equipmentId,
                repairRequestId,
                DefectStatus.OPEN,
                0
        );

        saveDefect(
                "DEF-002",
                "Pump vibration",
                "Pump vibration detected",
                equipmentId,
                repairRequestId,
                DefectStatus.OPEN,
                2
        );

        saveDefect(
                "DEF-003",
                "Resolved bearing issue",
                "Bearing issue fixed",
                equipmentId,
                repairRequestId,
                DefectStatus.RESOLVED,
                1
        );

        saveDefect(
                "DEF-004",
                "Closed defect",
                "Closed defect",
                equipmentId,
                repairRequestId,
                DefectStatus.CLOSED,
                0
        );

        DefectStatsProjection stats = repository.getDefectStats(
                null,
                null,
                null,
                DefectStatus.OPEN.name(),
                DefectStatus.RESOLVED.name()
        );

        assertThat(stats.getTotalDefects()).isEqualTo(4);
        assertThat(stats.getOpen()).isEqualTo(2);
        assertThat(stats.getResolved()).isEqualTo(1);
        assertThat(stats.getWithRecurrence()).isEqualTo(2);
    }

    @Test
    void getDefectStatsWithEquipmentRepairRequestAndSearchCountsOnlyMatchingDefects() {
        UUID targetEquipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        UUID targetRepairRequestId = UUID.randomUUID();
        UUID otherRepairRequestId = UUID.randomUUID();

        saveDefect(
                "DEF-PUMP-001",
                "Pump leak",
                "Pump has leak",
                targetEquipmentId,
                targetRepairRequestId,
                DefectStatus.OPEN,
                1
        );

        saveDefect(
                "DEF-PUMP-002",
                "Pump resolved",
                "Pump issue resolved",
                targetEquipmentId,
                targetRepairRequestId,
                DefectStatus.RESOLVED,
                0
        );

        saveDefect(
                "DEF-PUMP-003",
                "Other equipment pump",
                "Pump issue on another equipment",
                otherEquipmentId,
                targetRepairRequestId,
                DefectStatus.OPEN,
                1
        );

        saveDefect(
                "DEF-PUMP-004",
                "Other request pump",
                "Pump issue on another request",
                targetEquipmentId,
                otherRepairRequestId,
                DefectStatus.OPEN,
                1
        );

        saveDefect(
                "DEF-MOTOR-001",
                "Motor issue",
                "Motor issue",
                targetEquipmentId,
                targetRepairRequestId,
                DefectStatus.OPEN,
                1
        );

        DefectStatsProjection stats = repository.getDefectStats(
                targetEquipmentId,
                targetRepairRequestId,
                "%pump%",
                DefectStatus.OPEN.name(),
                DefectStatus.RESOLVED.name()
        );

        assertThat(stats.getTotalDefects()).isEqualTo(2);
        assertThat(stats.getOpen()).isEqualTo(1);
        assertThat(stats.getResolved()).isEqualTo(1);
        assertThat(stats.getWithRecurrence()).isEqualTo(1);
    }

    private void saveDefect(
            String code,
            String title,
            String description,
            UUID equipmentId,
            UUID repairRequestId,
            DefectStatus status,
            int recurrenceCount
    ) {
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID());
        defect.setCode(code);
        defect.setTitle(title);
        defect.setDescription(description);
        defect.setEquipmentId(equipmentId);
        defect.setRepairRequestId(repairRequestId);
        defect.setCategory("MECHANICAL");
        defect.setSeverity("HIGH");
        defect.setFailureReason("Wear");
        defect.setRootCause("Bearing wear");
        defect.setStatus(status);
        defect.setRecurrenceCount(recurrenceCount);
        defect.setDeleted(false);

        repository.save(defect);
    }
}