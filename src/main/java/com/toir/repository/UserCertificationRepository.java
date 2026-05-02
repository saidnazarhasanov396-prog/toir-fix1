package com.toir.repository;

import com.toir.entity.UserCertification;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface UserCertificationRepository extends JpaRepository<UserCertification, UUID> {
    @Query(value = "SELECT * FROM user_certifications WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<UserCertification> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM user_certifications WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<UserCertification> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query("""
            SELECT uc FROM UserCertification uc
                WHERE uc.isDeleted = false
                AND (CAST(:search AS string) IS NULL OR CAST(:search AS string) = '' OR
                    LOWER(uc.typeCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
                    LOWER(uc.certificateNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
                ORDER BY uc.updatedAt DESC
    """)
    List<UserCertification> findUserCertifications(
            @Param("search") String search
    );

    @Query(value = "SELECT * FROM user_certifications WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<UserCertification> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM user_certifications WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM user_certifications WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM user_certifications WHERE user_id = :userId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<UserCertification> findAllByUserIdAndIsDeletedFalse(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM user_certifications WHERE status = :status AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<UserCertification> findAllByStatusAndIsDeletedFalse(@Param("status") String status);

    @Query(value = "SELECT * FROM user_certifications WHERE expires_at < :date AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<UserCertification> findAllByExpiresAtBeforeAndIsDeletedFalse(@Param("date") LocalDate date);
}
