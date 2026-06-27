package com.toir.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mxik")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mxik extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String kod;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "position_name")
    private String positionName;
}
