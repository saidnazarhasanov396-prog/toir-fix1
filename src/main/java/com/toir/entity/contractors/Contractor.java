package com.toir.entity.contractors;

import com.toir.entity.BaseEntity;
import com.toir.entity.common.BankAccount;
import com.toir.enums.ContractorStatus;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Entity
@Table(name = "contractors")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Contractor extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "tax_number", length = 32)
    private String taxNumber;

    @Column(name = "contact_person")
    private String contactPerson;

    private String phone;
    private String email;
    private String specialization;

    @Column(name = "director_name")
    private String directorName;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "contractor_bank_accounts",
            joinColumns = @JoinColumn(name = "contractor_id")
    )
    @OrderColumn(name = "account_order")
    private List<BankAccount> bankAccounts = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractorStatus status = ContractorStatus.ACTIVE;

}