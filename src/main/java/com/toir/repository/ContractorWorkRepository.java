package com.toir.repository;
import com.toir.entity.ContractorWork;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContractorWorkRepository extends JpaRepository<ContractorWork, UUID> {
    List<ContractorWork> findAllByContractorId(UUID contractorId);
    List<ContractorWork> findAllByWorkOrderId(UUID workOrderId);
}
