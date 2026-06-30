package com.toir.entity;

import com.toir.enums.CounteragentStatus;
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
@Table(name = "counteragents")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Counteragent extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "tax_number")
    private String taxNumber;

    @Column(name = "base_inn")
    private String baseInn;

    @Column(name = "contact_person")
    private String contactPerson;

    private String phone;

    private String email;

    @Column(columnDefinition = "text")
    private String address;

    private String specialization;

    @Column(name = "director_name")
    private String directorName;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    private String mfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CounteragentStatus status = CounteragentStatus.ACTIVE;
}
