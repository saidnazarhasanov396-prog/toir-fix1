package com.toir.repository.contarctor;

import com.toir.entity.contractors.ContractorContract;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ContractorContractRepository extends JpaRepository<ContractorContract, UUID> {
    @Query(value = "SELECT * FROM contractor_contracts WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ContractorContract> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM contractor_contracts WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ContractorContract> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM contractor_contracts WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ContractorContract> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM contractor_contracts WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM contractor_contracts WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM contractor_contracts WHERE number = :number AND is_deleted = false)", nativeQuery = true)
    boolean existsByNumberAndIsDeletedFalse(@Param("number") String number);

    @Query(value = "SELECT * FROM contractor_contracts WHERE contractor_id = :contractorId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ContractorContract> findAllByContractorIdAndIsDeletedFalse(@Param("contractorId") UUID contractorId);
}
