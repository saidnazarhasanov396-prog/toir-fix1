package com.toir.dto.supplier;

import com.toir.dto.common.BankAccountDto;
import com.toir.enums.SupplierType;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

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
        List<BankAccountDto> bankAccounts,
        Boolean active,
        SupplierType supplierType
) {
}
