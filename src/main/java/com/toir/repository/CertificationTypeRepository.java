package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CertificationType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface CertificationTypeRepository extends JpaRepository<CertificationType, UUID> {
    boolean existsByCode(String code);
    CertificationType findByCode(String code);
}
