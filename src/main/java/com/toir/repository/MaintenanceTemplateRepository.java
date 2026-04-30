package com.toir.repository;

import com.toir.enums.MaintenanceKind;
import org.springframework.stereotype.Repository;
import com.toir.entity.MaintenanceTemplate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


@Repository
public interface MaintenanceTemplateRepository extends JpaRepository<MaintenanceTemplate, UUID> {
    java.util.Optional<MaintenanceTemplate> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<MaintenanceTemplate> findAllByIsDeletedFalse();

    java.util.List<MaintenanceTemplate> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM maintenance_templates WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query("""
    select mt from MaintenanceTemplate mt where
        mt.isDeleted = false and
         (:search is null or lower(mt.code) like lower(concat('%', :search ,'%')) or
         :search is null or lower(mt.description) like lower(concat('%', :search ,'%')) or
         :search is null or lower(mt.name) like lower(concat('%', :search ,'%')))
         or (:type is null or mt.maintenanceKind = :type)
""")
    List<MaintenanceTemplate> findAllByIsDeletedFalseAndMaintenanceKindAndSearch(String search, String type);

}
