package com.toir.dto.supplier;

import com.toir.entity.Supplier;

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
        Boolean active,
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
                supplier.getActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }
}
