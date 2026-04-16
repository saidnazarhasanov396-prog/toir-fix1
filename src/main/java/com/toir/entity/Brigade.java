package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ремонтная бригада — группа исполнителей с бригадиром, закреплённая
 * за подразделением. Используется для назначения на наряды и ведения
 * журнала трудозатрат.
 */
@Entity
@Table(name = "brigades")
public class Brigade extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "foreman_id")
    private UUID foremanId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String specialization;

    @OneToMany(mappedBy = "brigade", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BrigadeMember> members = new ArrayList<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getForemanId() { return foremanId; }
    public void setForemanId(UUID foremanId) { this.foremanId = foremanId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public List<BrigadeMember> getMembers() { return members; }
    public void setMembers(List<BrigadeMember> members) { this.members = members; }
}
