package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.SparePart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface SparePartRepository extends JpaRepository<SparePart, UUID> {
    java.util.Optional<SparePart> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<SparePart> findAllByIsDeletedFalse();

    java.util.List<SparePart> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM spare_parts WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
