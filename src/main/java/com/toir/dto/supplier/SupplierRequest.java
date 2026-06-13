package com.toir.dto.supplier;

import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
        String code,
        @NotBlank String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String taxNumber,
        Boolean active
) {
}
