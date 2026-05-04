package com.toir.entity.users;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
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
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
}
