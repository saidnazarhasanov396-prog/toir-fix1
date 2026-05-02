package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.BrigadeMember;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface BrigadeMemberRepository extends JpaRepository<BrigadeMember, UUID> {
    java.util.Optional<BrigadeMember> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<BrigadeMember> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<BrigadeMember> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM brigade_members WHERE brigade_id = :brigadeId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<BrigadeMember> findAllByBrigadeIdAndIsDeletedFalse(@Param("brigadeId") UUID brigadeId);

    @Query(value = "SELECT * FROM brigade_members WHERE user_id = :userId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<BrigadeMember> findAllByUserIdAndIsDeletedFalse(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM brigade_members WHERE brigade_id = :brigadeId AND user_id = :userId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<BrigadeMember> findByBrigadeIdAndUserIdAndIsDeletedFalse(@Param("brigadeId") UUID brigadeId, @Param("userId") UUID userId);
}
