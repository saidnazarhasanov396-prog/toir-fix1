package com.toir.entity;

import com.toir.entity.common.BankAccount;
import com.toir.enums.SupplierType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
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

    @Column(name = "tax_number", length = 32)
    private String taxNumber;

    @Column(name = "base_inn", length = 32)
    private String baseInn;

    @Column(name = "director_name")
    private String directorName;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "supplier_bank_accounts",
            joinColumns = @JoinColumn(name = "supplier_id")
    )
    @OrderColumn(name = "account_order")
    private List<BankAccount> bankAccounts = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "supplier_type", nullable = false, length = 32)
    @Builder.Default
    private SupplierType supplierType = SupplierType.BOTH;

    @Column(nullable = false)
    private Boolean active = true;
}
