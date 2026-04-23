package com.toir.dto.brigade;

import com.toir.entity.BrigadeMember;

import java.util.List;
import java.util.UUID;

public record BrigadeMemberDto(
        UUID id,
        UUID brigadeId,
        UUID userId,
        String roleCode,
        Integer grade,
        List<String> qualifications,
        boolean active
) {
    public static BrigadeMemberDto from(BrigadeMember m) {
        return new BrigadeMemberDto(
                m.getId(),
                m.getBrigade() != null ? m.getBrigade().getId() : null,
                m.getUserId(),
                m.getRoleCode(),
                m.getGrade(),
                m.getQualifications(),
                m.isActive()
        );
    }
}
