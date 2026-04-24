package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CertificationType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface CertificationTypeRepository extends JpaRepository<CertificationType, UUID> {
    @Query(value = "SELECT EXISTS(SELECT 1 FROM certification_types WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM certification_types WHERE code = :code AND is_deleted = false LIMIT 1", nativeQuery = true)
    CertificationType findByCode(@Param("code") String code);
}
