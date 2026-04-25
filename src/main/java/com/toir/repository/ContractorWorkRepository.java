package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ContractorWork;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface ContractorWorkRepository extends JpaRepository<ContractorWork, UUID> {
    java.util.Optional<ContractorWork> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ContractorWork> findAllByIsDeletedFalse();

    java.util.List<ContractorWork> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM contractor_works WHERE contractor_id = :contractorId AND is_deleted = false", nativeQuery = true)
    List<ContractorWork> findAllByContractorIdAndIsDeletedFalse(@Param("contractorId") UUID contractorId);

    @Query(value = "SELECT * FROM contractor_works WHERE work_order_id = :workOrderId AND is_deleted = false", nativeQuery = true)
    List<ContractorWork> findAllByWorkOrderIdAndIsDeletedFalse(@Param("workOrderId") UUID workOrderId);
}
