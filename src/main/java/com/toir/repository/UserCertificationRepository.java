package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.UserCertification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Repository
public interface UserCertificationRepository extends JpaRepository<UserCertification, UUID> {
    List<UserCertification> findAllByUserId(UUID userId);
    List<UserCertification> findAllByStatus(String status);
    List<UserCertification> findAllByExpiresAtBefore(LocalDate date);
}
