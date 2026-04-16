package com.toir.equipmentpassport;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equipment_passports")
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

    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }
    public String getFactoryNumber() { return factoryNumber; }
    public void setFactoryNumber(String factoryNumber) { this.factoryNumber = factoryNumber; }
    public String getManufacturerSerial() { return manufacturerSerial; }
    public void setManufacturerSerial(String manufacturerSerial) { this.manufacturerSerial = manufacturerSerial; }
    public Double getPowerKw() { return powerKw; }
    public void setPowerKw(Double powerKw) { this.powerKw = powerKw; }
    public Double getVoltageV() { return voltageV; }
    public void setVoltageV(Double voltageV) { this.voltageV = voltageV; }
    public Double getPressureBar() { return pressureBar; }
    public void setPressureBar(Double pressureBar) { this.pressureBar = pressureBar; }
    public Double getThroughput() { return throughput; }
    public void setThroughput(Double throughput) { this.throughput = throughput; }
    public LocalDate getInstallDate() { return installDate; }
    public void setInstallDate(LocalDate installDate) { this.installDate = installDate; }
    public LocalDate getLastInspectionDate() { return lastInspectionDate; }
    public void setLastInspectionDate(LocalDate lastInspectionDate) { this.lastInspectionDate = lastInspectionDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
