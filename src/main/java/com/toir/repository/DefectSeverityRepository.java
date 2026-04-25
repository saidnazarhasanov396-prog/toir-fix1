package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.DefectSeverity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface DefectSeverityRepository extends JpaRepository<DefectSeverity, UUID> {
    java.util.Optional<DefectSeverity> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<DefectSeverity> findAllByIsDeletedFalse();

    java.util.List<DefectSeverity> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM defect_severities WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
