package com.toir.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class ActorStampedEntity extends BaseEntity {

    @CreatedBy
    @Column(name = "created_by_id")
    private UUID createdById;

    @LastModifiedBy
    @Column(name = "updated_by_id")
    private UUID updatedById;
}
