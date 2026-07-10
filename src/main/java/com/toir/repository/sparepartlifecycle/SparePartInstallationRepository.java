package com.toir.repository.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SparePartInstallationRepository extends JpaRepository<SparePartInstallation, UUID>,
        JpaSpecificationExecutor<SparePartInstallation> {

    Optional<SparePartInstallation> findByIdAndIsDeletedFalse(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select installation
            from SparePartInstallation installation
            where installation.id = :id
              and installation.isDeleted = false
            """)
    Optional<SparePartInstallation> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select installation
            from SparePartInstallation installation
            where installation.equipmentId = :equipmentId
              and installation.positionKey = :positionKey
              and installation.status = com.toir.enums.sparepartlifecycle.SparePartInstallationStatus.ACTIVE
              and installation.isDeleted = false
            """)
    Optional<SparePartInstallation> findActiveByEquipmentIdAndPositionKeyForUpdate(
            @Param("equipmentId") UUID equipmentId,
            @Param("positionKey") String positionKey
    );

    boolean existsBySparePartIdAndSerialNumberSnapshotIgnoreCaseAndIsDeletedFalse(
            UUID sparePartId,
            String serialNumberSnapshot
    );

    List<SparePartInstallation> findAllByEquipmentIdAndStatusAndIsDeletedFalseOrderByInstalledAtDesc(
            UUID equipmentId,
            SparePartInstallationStatus status
    );

    List<SparePartInstallation> findAllByEquipmentIdAndIsDeletedFalseOrderByInstalledAtDesc(UUID equipmentId);

    List<SparePartInstallation> findAllByStatusAndIsDeletedFalseOrderByInstalledAtAsc(
            SparePartInstallationStatus status
    );

    List<SparePartInstallation> findAllByIdInAndStatusAndIsDeletedFalse(
            List<UUID> ids,
            SparePartInstallationStatus status
    );
}
