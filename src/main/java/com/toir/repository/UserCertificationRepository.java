package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.UserCertification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Repository
public interface UserCertificationRepository extends JpaRepository<UserCertification, UUID> {
    @Query(value = "SELECT * FROM user_certifications WHERE user_id = :userId AND is_deleted = false", nativeQuery = true)
    List<UserCertification> findAllByUserId(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM user_certifications WHERE status = :status AND is_deleted = false", nativeQuery = true)
    List<UserCertification> findAllByStatus(@Param("status") String status);

    @Query(value = "SELECT * FROM user_certifications WHERE expires_at < :date AND is_deleted = false", nativeQuery = true)
    List<UserCertification> findAllByExpiresAtBefore(@Param("date") LocalDate date);
}
