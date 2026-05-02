package com.toir.repository;

import com.toir.entity.CertificationType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface CertificationTypeRepository extends JpaRepository<CertificationType, UUID> {
    @Query(value = "SELECT * FROM certification_types WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<CertificationType> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query("""
    SELECT ct FROM CertificationType ct
        join UserCertification uc on ct.code = uc.typeCode
        join User u on u.id = uc.userId
    WHERE ct.isDeleted = false
            AND (:code IS NULL OR ct.code = :code)
            AND (:name IS NULL OR ct.name = :name)
            AND (:search IS NULL OR u.fullName = :search)
            ORDER BY ct.updatedAt DESC
            """)
    @Query(value = "SELECT * FROM certification_types WHERE is_deleted = false", nativeQuery = true)
    List<CertificationType> findAllByIsDeletedFalse(@Param("code") String code, @Param("name")  String name, @Param("search") String search);

    @Query(value = "SELECT * FROM certification_types WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<CertificationType> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM certification_types WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM certification_types WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM certification_types WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM certification_types WHERE code = :code AND is_deleted = false LIMIT 1", nativeQuery = true)
    CertificationType findByCodeAndIsDeletedFalse(@Param("code") String code);
}
