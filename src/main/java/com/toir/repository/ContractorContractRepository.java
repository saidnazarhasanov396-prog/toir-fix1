package com.toir.repository;
import com.toir.entity.ContractorContract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContractorContractRepository extends JpaRepository<ContractorContract, UUID> {
    boolean existsByNumber(String number);
    List<ContractorContract> findAllByContractorId(UUID contractorId);
}
