package com.toir.entity.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account", length = 64)
    private String bankAccount;

    @Column(name = "mfo", length = 32)
    private String mfo;
}
