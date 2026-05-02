package com.toir.dto.manufacturer;

import com.toir.entity.Manufacturer;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ManufacturerDto(
        UUID id,
        String code,
        @NotBlank String name,
        String country,
        String website,
        String contactInfo
) {
    public static ManufacturerDto from(Manufacturer m) {
        return new ManufacturerDto(m.getId(), m.getCode(), m.getName(), m.getCountry(), m.getWebsite(), m.getContactInfo());
    }
}
