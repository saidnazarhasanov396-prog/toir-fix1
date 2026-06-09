package com.toir.repository.maintenance;

import com.toir.entity.maintenance.RegulationChangeProposal;
import com.toir.enums.RegulationChangeProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulationChangeProposalRepository extends JpaRepository<RegulationChangeProposal, UUID> {

    List<RegulationChangeProposal> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    List<RegulationChangeProposal> findAllByRegulationIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID regulationId);

    List<RegulationChangeProposal> findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(RegulationChangeProposalStatus status);

    Optional<RegulationChangeProposal> findByIdAndIsDeletedFalse(UUID id);
}