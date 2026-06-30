package com.toir.repository.contarctor;

import com.toir.entity.contractors.ContractorWork;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ContractorWorkRepository extends JpaRepository<ContractorWork, UUID> {
    @Query(value = "SELECT * FROM contractor_works WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ContractorWork> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM contractor_works WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ContractorWork> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM contractor_works WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ContractorWork> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM contractor_works WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM contractor_works WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    default List<ContractorWork> findAllByOptionalCounteragentIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID counteragentId) {
        if (counteragentId != null) {
            return findAllByCounteragentIdAndIsDeletedFalse(counteragentId);
        }
        return findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    }

    @Query(value = "SELECT * FROM contractor_works WHERE counteragent_id = :counteragentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ContractorWork> findAllByCounteragentIdAndIsDeletedFalse(@Param("counteragentId") UUID counteragentId);

    @Query(value = "SELECT * FROM contractor_works WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ContractorWork> findAllByWorkOrderIdAndIsDeletedFalse(@Param("workOrderId") UUID workOrderId);

    @Query(value = """
            SELECT *
            FROM contractor_works
            WHERE work_order_id IN (:workOrderIds)
              AND is_deleted = false
            ORDER BY updated_at DESC
            """, nativeQuery = true)
    List<ContractorWork> findAllByWorkOrderIdInAndIsDeletedFalse(@Param("workOrderIds") Collection<UUID> workOrderIds);
}
