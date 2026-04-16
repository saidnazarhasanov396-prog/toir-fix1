package com.toir.certification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CertificationTypeRepository extends JpaRepository<CertificationType, UUID> {
    boolean existsByCode(String code);
    CertificationType findByCode(String code);
}
