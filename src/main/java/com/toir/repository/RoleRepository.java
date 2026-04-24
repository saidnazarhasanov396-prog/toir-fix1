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
    @Query(value = "SELECT * FROM roles WHERE code = :code LIMIT 1", nativeQuery = true)
    Optional<Role> findByCode(@Param("code") String code);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM roles WHERE code = :code)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);
}
