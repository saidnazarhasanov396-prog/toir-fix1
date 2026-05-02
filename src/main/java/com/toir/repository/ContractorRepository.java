package com.toir.repository;

import com.toir.entity.Contractor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ContractorRepository extends JpaRepository<Contractor, UUID> {
    @Query(value = "SELECT * FROM contractors WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Contractor> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM contractors WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Contractor> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM contractors WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Contractor> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM contractors WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM contractors WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM contractors WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
