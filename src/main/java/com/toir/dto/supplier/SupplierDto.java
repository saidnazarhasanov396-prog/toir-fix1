package com.toir.dto.supplier;

import com.toir.entity.Supplier;
import com.toir.enums.SupplierType;

import java.time.Instant;
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
        String bankName,
        String bankAccount,
        String mfo,
        Boolean active,
        SupplierType supplierType,
        Instant createdAt,
        Instant updatedAt
) {
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
                supplier.getBankName(),
                supplier.getBankAccount(),
                supplier.getMfo(),
                supplier.getActive(),
                supplier.getSupplierType() == null ? SupplierType.BOTH : supplier.getSupplierType(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }
}
