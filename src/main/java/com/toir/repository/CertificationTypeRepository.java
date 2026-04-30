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

    @Query("""
    SELECT ct FROM CertificationType ct
        join UserCertification uc on ct.code = uc.typeCode
        join User u on u.id = uc.userId
    WHERE ct.isDeleted = false
            AND (:code IS NULL OR ct.code = :code)
            AND (:name IS NULL OR ct.name = :name)
            AND (:search IS NULL OR u.fullName = :search)
            """)
    java.util.List<CertificationType> findAllByIsDeletedFalse(
            @Param("code") String code,
            @Param("name")  String name,
            @Param("search") String search
    );

    java.util.List<CertificationType> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM certification_types WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM certification_types WHERE code = :code AND is_deleted = false LIMIT 1", nativeQuery = true)
    CertificationType findByCodeAndIsDeletedFalse(@Param("code") String code);
}
