package com.toir.repository;

import com.toir.entity.ExternalEntityLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalEntityLinkRepository extends JpaRepository<ExternalEntityLink, UUID> {

    Optional<ExternalEntityLink> findBySourceSystemAndSourceEntityTypeAndSourceEntityIdAndIsDeletedFalse(
            String sourceSystem,
            String sourceEntityType,
            String sourceEntityId
    );

    Optional<ExternalEntityLink> findByTargetSystemAndTargetEntityTypeAndTargetEntityIdAndIsDeletedFalse(
            String targetSystem,
            String targetEntityType,
            UUID targetEntityId
    );
}
