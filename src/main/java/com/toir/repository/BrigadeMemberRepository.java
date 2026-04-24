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
    @Query(value = "SELECT * FROM brigade_members WHERE brigade_id = :brigadeId AND is_deleted = false", nativeQuery = true)
    List<BrigadeMember> findAllByBrigadeId(@Param("brigadeId") UUID brigadeId);

    @Query(value = "SELECT * FROM brigade_members WHERE user_id = :userId AND is_deleted = false", nativeQuery = true)
    List<BrigadeMember> findAllByUserId(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM brigade_members WHERE brigade_id = :brigadeId AND user_id = :userId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<BrigadeMember> findByBrigadeIdAndUserId(@Param("brigadeId") UUID brigadeId, @Param("userId") UUID userId);
}
