package com.toir.dto.supplier;

import com.toir.enums.SupplierType;
import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
        String code,
        @NotBlank String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String taxNumber,
        String baseInn,
        String directorName,
        String bankName,
        String bankAccount,
        String mfo,
        Boolean active,
        SupplierType supplierType
) {
}
