package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.BrigadeMember;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface BrigadeMemberRepository extends JpaRepository<BrigadeMember, UUID> {
    List<BrigadeMember> findAllByBrigadeId(UUID brigadeId);
    List<BrigadeMember> findAllByUserId(UUID userId);
    Optional<BrigadeMember> findByBrigadeIdAndUserId(UUID brigadeId, UUID userId);
}
