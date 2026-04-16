package com.toir.brigade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrigadeMemberRepository extends JpaRepository<BrigadeMember, UUID> {
    List<BrigadeMember> findAllByBrigadeId(UUID brigadeId);
    List<BrigadeMember> findAllByUserId(UUID userId);
    Optional<BrigadeMember> findByBrigadeIdAndUserId(UUID brigadeId, UUID userId);
}
