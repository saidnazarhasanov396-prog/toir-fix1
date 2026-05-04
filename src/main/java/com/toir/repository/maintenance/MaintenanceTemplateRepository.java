package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.enums.MaintenanceKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface MaintenanceTemplateRepository extends JpaRepository<MaintenanceTemplate, UUID> {
    @Query(value = "SELECT * FROM maintenance_templates WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<MaintenanceTemplate> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM maintenance_templates WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceTemplate> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM maintenance_templates WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<MaintenanceTemplate> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM maintenance_templates WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM maintenance_templates WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM maintenance_templates WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query("""
        select mt from MaintenanceTemplate mt
        where mt.isDeleted = false
        and (
            :search is null or (
                lower(mt.code) like :search or
                lower(mt.description) like :search or
                lower(mt.name) like :search
            )
        )
        and ( :type is null or mt.maintenanceKind = :type)
        order by mt.updatedAt desc
""")
    List<MaintenanceTemplate> findAllByIsDeletedFalseAndMaintenanceKindAndSearch(String search, MaintenanceKind type);

}
