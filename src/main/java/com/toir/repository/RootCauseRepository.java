package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.RootCause;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface RootCauseRepository extends JpaRepository<RootCause, UUID> {
    java.util.Optional<RootCause> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<RootCause> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<RootCause> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM root_causes WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
