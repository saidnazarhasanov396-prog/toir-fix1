package com.toir.dto.brigade;

import com.toir.entity.users.Brigade;

import java.util.List;
import java.util.UUID;

public record BrigadeDto(
        UUID id,
        String code,
        String name,
        UUID departmentId,
        UUID foremanId,
        String specialization,
        boolean active,
        List<BrigadeMemberDto> members
) {
    public static BrigadeDto from(Brigade b) {
        return new BrigadeDto(
                b.getId(),
                b.getCode(),
                b.getName(),
                b.getDepartmentId(),
                b.getForemanId(),
                b.getSpecialization(),
                b.isActive(),
                b.getMembers() == null ? List.of() : b.getMembers().stream().map(BrigadeMemberDto::from).toList()
        );
    }
}
