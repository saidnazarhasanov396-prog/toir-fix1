package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Contractor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface ContractorRepository extends JpaRepository<Contractor, UUID> {
    java.util.Optional<Contractor> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Contractor> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<Contractor> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM contractors WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
