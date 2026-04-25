package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ContractorContract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface ContractorContractRepository extends JpaRepository<ContractorContract, UUID> {
    java.util.Optional<ContractorContract> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ContractorContract> findAllByIsDeletedFalse();

    java.util.List<ContractorContract> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM contractor_contracts WHERE number = :number AND is_deleted = false)", nativeQuery = true)
    boolean existsByNumberAndIsDeletedFalse(@Param("number") String number);

    @Query(value = "SELECT * FROM contractor_contracts WHERE contractor_id = :contractorId AND is_deleted = false", nativeQuery = true)
    List<ContractorContract> findAllByContractorIdAndIsDeletedFalse(@Param("contractorId") UUID contractorId);
}
