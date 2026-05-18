package com.toir.service.defects;


import com.toir.dto.defectlist.DefectListDto;
import com.toir.entity.defects.DefectList;
import com.toir.enums.DefectListStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefectListDtoTest {

    @Test
    void fromIncludesCreatedAt() {
        Instant createdAt = Instant.parse("2026-05-18T06:15:30Z");

        DefectList entity = new DefectList();
        entity.setId(UUID.randomUUID());
        entity.setCreatedAt(createdAt);
        entity.setCode("DL-2026-0001");
        entity.setTitle("Test defect list");
        entity.setEquipmentId(UUID.randomUUID());
        entity.setCreatedById(UUID.randomUUID());
        entity.setStatus(DefectListStatus.DRAFT);
        entity.setNotes("note");

        DefectListDto dto = DefectListDto.from(entity);

        assertThat(dto.createdAt()).isEqualTo(createdAt);
    }
}