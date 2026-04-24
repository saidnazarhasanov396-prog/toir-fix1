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
    @Query(value = "SELECT * FROM contractor_works WHERE contractor_id = :contractorId AND is_deleted = false", nativeQuery = true)
    List<ContractorWork> findAllByContractorId(@Param("contractorId") UUID contractorId);

    @Query(value = "SELECT * FROM contractor_works WHERE work_order_id = :workOrderId AND is_deleted = false", nativeQuery = true)
    List<ContractorWork> findAllByWorkOrderId(@Param("workOrderId") UUID workOrderId);
}
