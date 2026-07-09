package com.toir.dto.defect;

import com.toir.entity.defects.Defect;
import com.toir.enums.DefectStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class DefectDtoTest {

    @Test
    void inspectionAutoDefectExposesTranslationKeysWithoutComment() {
        Defect defect = defect(
                "Inspection failure: Brake fluid level",
                "Inspection FAIL triage. roundId=round-1; checkpointId=checkpoint-1; checkpoint=Brake fluid level. "
                        + "Checkpoint failed and requires maintenance triage."
        );

        DefectDto dto = DefectDto.from(defect);

        assertThat(dto.titleKey()).isEqualTo("defects.inspectionAutoDefect.title");
        assertThat(dto.titleParams()).containsEntry("checkpointTitle", "Brake fluid level");
        assertThat(dto.messageKey()).isEqualTo("defects.inspectionAutoDefect.messageNoComment");
        assertThat(dto.messageParams())
                .containsEntry("roundId", "round-1")
                .containsEntry("checkpointId", "checkpoint-1")
                .containsEntry("checkpointTitle", "Brake fluid level")
                .doesNotContainKey("comment");
    }

    @Test
    void inspectionAutoDefectExposesTranslationKeysWithComment() {
        Defect defect = defect(
                "Inspection failure: Brake fluid level",
                "Inspection FAIL triage. roundId=round-1; checkpointId=checkpoint-1; checkpoint=Brake fluid level. "
                        + "Fluid visibly below minimum mark."
        );

        DefectDto dto = DefectDto.from(defect);

        assertThat(dto.messageKey()).isEqualTo("defects.inspectionAutoDefect.messageWithComment");
        assertThat(dto.messageParams()).containsEntry("comment", "Fluid visibly below minimum mark.");
    }

    @Test
    void manuallyCreatedDefectHasNoTranslationKeys() {
        Defect defect = defect("Pump bearing worn out", "Noticed unusual vibration during shift");

        DefectDto dto = DefectDto.from(defect);

        assertThat(dto.titleKey()).isNull();
        assertThat(dto.titleParams()).isNull();
        assertThat(dto.messageKey()).isNull();
        assertThat(dto.messageParams()).isNull();
        assertThat(dto.title()).isEqualTo("Pump bearing worn out");
    }

    private Defect defect(String title, String description) {
        Defect defect = new Defect();
        ReflectionTestUtils.setField(defect, "id", UUID.randomUUID());
        defect.setCode("DEF-001");
        defect.setTitle(title);
        defect.setDescription(description);
        defect.setEquipmentId(UUID.randomUUID());
        defect.setStatus(DefectStatus.OPEN);
        defect.setDetectedAt(Instant.now());
        return defect;
    }
}
