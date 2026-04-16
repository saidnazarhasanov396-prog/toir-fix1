package com.toir.manufacturer.dto;

import com.toir.manufacturer.Manufacturer;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ManufacturerDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        String country,
        String website,
        String contactInfo
) {
    public static ManufacturerDto from(Manufacturer m) {
        return new ManufacturerDto(m.getId(), m.getCode(), m.getName(), m.getCountry(), m.getWebsite(), m.getContactInfo());
    }
}
