package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * Член бригады — пользователь с квалификациями и ролью в бригаде.
 */
@Entity
@Table(name = "brigade_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"brigade_id", "user_id"}))
public class BrigadeMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brigade_id", nullable = false)
    private Brigade brigade;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Роль в бригаде: FOREMAN, WELDER, LOCKSMITH, ELECTRICIAN, INSTRUMENT, HELPER, etc. */
    @Column(name = "role_code", nullable = false)
    private String roleCode;

    /** Grade / разряд (1..8 for mechanic). */
    @Column(name = "grade")
    private Integer grade;

    /** Free-form list of skill codes (e.g. ["WELDING", "NDT", "HIGH_ALTITUDE"]). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> qualifications;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Brigade getBrigade() { return brigade; }
    public void setBrigade(Brigade brigade) { this.brigade = brigade; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }
    public List<String> getQualifications() { return qualifications; }
    public void setQualifications(List<String> qualifications) { this.qualifications = qualifications; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
