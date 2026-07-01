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

    @Column(length = 9)
    private String inn;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_position")
    private String contactPosition;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(columnDefinition = "text")
    private String address;

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
