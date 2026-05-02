package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    java.util.Optional<Role> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Role> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<Role> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM roles WHERE code = :code AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Role> findByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM roles WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
