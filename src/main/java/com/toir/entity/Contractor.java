package com.toir.entity;

import com.toir.enums.ContractorStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "contractors")
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractorStatus status = ContractorStatus.ACTIVE;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTaxNumber() { return taxNumber; }
    public void setTaxNumber(String taxNumber) { this.taxNumber = taxNumber; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public ContractorStatus getStatus() { return status; }
    public void setStatus(ContractorStatus status) { this.status = status; }
}
