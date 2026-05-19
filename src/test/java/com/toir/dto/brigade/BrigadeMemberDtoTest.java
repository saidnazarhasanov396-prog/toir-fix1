package com.toir.dto.brigade;

import com.toir.entity.users.BrigadeMember;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrigadeMemberDtoTest {

    @Test
    void fromWithNullQualificationsReturnsEmptyList() {
        BrigadeMember member = new BrigadeMember();
        member.setQualifications(null);

        BrigadeMemberDto dto = BrigadeMemberDto.from(member);

        assertThat(dto.qualifications()).isEmpty();
    }
}
