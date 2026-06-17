package com.toir.dto.hr;

import com.toir.entity.users.EmployeeSpecialisation;

import java.util.UUID;

public record EmployeeSpecialisationDto(
        UUID id,
        String nameRu,
        String nameEn,
        String nameUz,
        boolean active
) {
    public static EmployeeSpecialisationDto from(EmployeeSpecialisation specialisation) {
        return new EmployeeSpecialisationDto(
                specialisation.getId(),
                specialisation.getNameRu(),
                specialisation.getNameEn(),
                specialisation.getNameUz(),
                specialisation.isActive()
        );
    }
}
