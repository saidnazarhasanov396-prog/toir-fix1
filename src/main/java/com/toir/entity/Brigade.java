package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

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
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

}
