package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CertificationType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface CertificationTypeRepository extends JpaRepository<CertificationType, UUID> {
    java.util.Optional<CertificationType> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<CertificationType> findAllByIsDeletedFalse();

    java.util.List<CertificationType> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM certification_types WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM certification_types WHERE code = :code AND is_deleted = false LIMIT 1", nativeQuery = true)
    CertificationType findByCodeAndIsDeletedFalse(@Param("code") String code);
}
