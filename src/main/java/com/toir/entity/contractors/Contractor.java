package com.toir.entity.contractors;

import com.toir.entity.BaseEntity;
import com.toir.enums.ContractorStatus;
import jakarta.persistence.*;
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

    @Column(name = "tax_number")
    private String taxNumber;

    @Column(name = "contact_person")
    private String contactPerson;

    private String phone;
    private String email;
    private String specialization;

    @Column(name = "director_name")
    private String directorName;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "mfo")
    private String mfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractorStatus status = ContractorStatus.ACTIVE;

}
