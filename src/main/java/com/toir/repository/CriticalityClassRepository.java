package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CriticalityClass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface CriticalityClassRepository extends JpaRepository<CriticalityClass, UUID> {
    java.util.Optional<CriticalityClass> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<CriticalityClass> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<CriticalityClass> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM criticality_classes WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
