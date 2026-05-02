package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Material;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {
    java.util.Optional<Material> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Material> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<Material> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM materials WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
