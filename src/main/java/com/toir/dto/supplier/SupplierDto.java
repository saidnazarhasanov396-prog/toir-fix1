package com.toir.dto.supplier;

import com.toir.dto.common.BankAccountDto;
import com.toir.entity.Supplier;
import com.toir.enums.SupplierType;
import com.toir.util.PartyLegalDetailsUtils;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupplierDto(
        UUID id,
        String code,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String taxNumber,
        String baseInn,
        String directorName,
        List<BankAccountDto> bankAccounts,
        Boolean active,
        SupplierType supplierType,
        Instant createdAt,
        Instant updatedAt
) {
    public SupplierDto {
        bankAccounts = bankAccounts == null ? List.of() : List.copyOf(bankAccounts);
    }

    public static SupplierDto from(Supplier supplier) {
        return new SupplierDto(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getContactPerson(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getTaxNumber(),
                supplier.getBaseInn(),
                supplier.getDirectorName(),
                PartyLegalDetailsUtils.toBankAccountDtos(supplier.getBankAccounts()),
                supplier.getActive(),
                supplier.getSupplierType() == null ? SupplierType.BOTH : supplier.getSupplierType(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }
}
