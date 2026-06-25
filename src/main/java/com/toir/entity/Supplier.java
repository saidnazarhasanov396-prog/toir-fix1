package com.toir.entity;

import com.toir.enums.SupplierType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Supplier extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_person")
    private String contactPerson;

    private String phone;

    private String email;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "tax_number")
    private String taxNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "supplier_type", nullable = false, length = 32)
    @Builder.Default
    private SupplierType supplierType = SupplierType.BOTH;

    @Column(nullable = false)
    private Boolean active = true;
}
