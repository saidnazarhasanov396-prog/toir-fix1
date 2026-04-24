package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equipment_passports")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EquipmentPassport extends BaseEntity {

    @Column(name = "equipment_id", nullable = false, unique = true)
    private UUID equipmentId;

    @Column(name = "passport_number", unique = true)
    private String passportNumber;

    @Column(name = "factory_number")
    private String factoryNumber;

    @Column(name = "manufacturer_serial")
    private String manufacturerSerial;

    @Column(name = "power_kw")
    private Double powerKw;

    @Column(name = "voltage_v")
    private Double voltageV;

    @Column(name = "pressure_bar")
    private Double pressureBar;

    private Double throughput;

    @Column(name = "install_date")
    private LocalDate installDate;

    @Column(name = "last_inspection_date")
    private LocalDate lastInspectionDate;

    @Column(columnDefinition = "text")
    private String notes;

}
