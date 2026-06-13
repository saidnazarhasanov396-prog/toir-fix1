package com.toir.repository;

import com.toir.dto.inventory.InventoryStatisticsDto;
import com.toir.entity.InventoryTransaction;
import com.toir.enums.InventoryTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    default Page<InventoryTransaction> findAllByFilter(
            boolean scopeAdmin,
            Collection<UUID> warehouseIds,
            InventoryTransactionType type,
            UUID warehouseId,
            UUID sparePartId,
            UUID workOrderId,
            UUID departmentId,
            UUID responsiblePersonId,
            UUID takenById,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        if (scopeAdmin) {
            return findAllByFilterAdmin(
                    type,
                    warehouseId,
                    sparePartId,
                    workOrderId,
                    departmentId,
                    responsiblePersonId,
                    takenById,
                    fromDate,
                    toDate,
                    pageable
            );
        }
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return findAllByFilterScoped(
                warehouseIds,
                type,
                warehouseId,
                sparePartId,
                workOrderId,
                departmentId,
                responsiblePersonId,
                takenById,
                fromDate,
                toDate,
                pageable
        );
    }

    default InventoryStatisticsDto getStatistics(
            boolean scopeAdmin,
            Collection<UUID> warehouseIds,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        List<InventoryTransaction> transactions;
        if (scopeAdmin) {
            transactions = findAllForStatisticsAdmin(fromDate, toDate);
        } else if (warehouseIds == null || warehouseIds.isEmpty()) {
            transactions = List.of();
        } else {
            transactions = findAllForStatisticsScoped(warehouseIds, fromDate, toDate);
        }

        long totalReceipts = transactions.stream()
                .filter(tx -> tx.getType() == InventoryTransactionType.RECEIPT)
                .count();
        long totalIssues = transactions.stream()
                .filter(tx -> tx.getType() == InventoryTransactionType.ISSUE)
                .count();
        long transfers = transactions.stream()
                .filter(tx -> tx.getType() == InventoryTransactionType.TRANSFER)
                .count();
        long returns = transactions.stream()
                .filter(tx -> tx.getType() == InventoryTransactionType.RETURN)
                .count();
        long adjustments = transactions.stream()
                .filter(tx -> tx.getType() == InventoryTransactionType.ADJUSTMENT)
                .count();
        BigDecimal receiptAmount = sum(transactions, InventoryTransactionType.RECEIPT, true);
        BigDecimal issueAmount = sum(transactions, InventoryTransactionType.ISSUE, true);
        BigDecimal receivedQuantity = sum(transactions, InventoryTransactionType.RECEIPT, false);
        BigDecimal issuedQuantity = sum(transactions, InventoryTransactionType.ISSUE, false);

        return new InventoryStatisticsDto(
                totalReceipts,
                totalIssues,
                transfers,
                returns,
                adjustments,
                transactions.size(),
                receiptAmount,
                issueAmount,
                receivedQuantity,
                issuedQuantity
        );
    }

    private static BigDecimal sum(
            Collection<InventoryTransaction> transactions,
            InventoryTransactionType type,
            boolean amount
    ) {
        return transactions.stream()
                .filter(tx -> tx.getType() == type)
                .map(tx -> amount ? tx.getTotalAmount() : tx.getQuantity())
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Query("""
            select tx
            from InventoryTransaction tx
            where (:type is null or tx.type = :type)
              and (:warehouseId is null or tx.warehouseId = :warehouseId)
              and (:sparePartId is null or tx.sparePartId = :sparePartId)
              and (:workOrderId is null or tx.workOrderId = :workOrderId)
              and (:departmentId is null or tx.departmentId = :departmentId)
              and (:responsiblePersonId is null or tx.responsiblePersonId = :responsiblePersonId)
              and (:takenById is null or tx.takenById = :takenById)
              and (:fromDate is null or tx.transactionDate >= :fromDate)
              and (:toDate is null or tx.transactionDate <= :toDate)
            """)
    Page<InventoryTransaction> findAllByFilterAdmin(
            @Param("type") InventoryTransactionType type,
            @Param("warehouseId") UUID warehouseId,
            @Param("sparePartId") UUID sparePartId,
            @Param("workOrderId") UUID workOrderId,
            @Param("departmentId") UUID departmentId,
            @Param("responsiblePersonId") UUID responsiblePersonId,
            @Param("takenById") UUID takenById,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("""
            select tx
            from InventoryTransaction tx
            where tx.warehouseId in :warehouseIds
              and (:type is null or tx.type = :type)
              and (:warehouseId is null or tx.warehouseId = :warehouseId)
              and (:sparePartId is null or tx.sparePartId = :sparePartId)
              and (:workOrderId is null or tx.workOrderId = :workOrderId)
              and (:departmentId is null or tx.departmentId = :departmentId)
              and (:responsiblePersonId is null or tx.responsiblePersonId = :responsiblePersonId)
              and (:takenById is null or tx.takenById = :takenById)
              and (:fromDate is null or tx.transactionDate >= :fromDate)
              and (:toDate is null or tx.transactionDate <= :toDate)
            """)
    Page<InventoryTransaction> findAllByFilterScoped(
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("type") InventoryTransactionType type,
            @Param("warehouseId") UUID warehouseId,
            @Param("sparePartId") UUID sparePartId,
            @Param("workOrderId") UUID workOrderId,
            @Param("departmentId") UUID departmentId,
            @Param("responsiblePersonId") UUID responsiblePersonId,
            @Param("takenById") UUID takenById,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("""
            select tx
            from InventoryTransaction tx
            where (:fromDate is null or tx.transactionDate >= :fromDate)
              and (:toDate is null or tx.transactionDate <= :toDate)
            """)
    List<InventoryTransaction> findAllForStatisticsAdmin(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            select tx
            from InventoryTransaction tx
            where tx.warehouseId in :warehouseIds
              and (:fromDate is null or tx.transactionDate >= :fromDate)
              and (:toDate is null or tx.transactionDate <= :toDate)
            """)
    List<InventoryTransaction> findAllForStatisticsScoped(
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    List<InventoryTransaction> findAllBySparePartIdAndTypeInOrderByTransactionDateDescCreatedAtDesc(
            UUID sparePartId,
            Collection<InventoryTransactionType> types
    );

    default List<InventoryTransaction> findAdjustmentsForReconciliation(
            boolean scopeAdmin,
            Collection<UUID> warehouseIds
    ) {
        if (scopeAdmin) {
            return findAllAdjustmentsForReconciliationAdmin();
        }
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return List.of();
        }
        return findAllAdjustmentsForReconciliationScoped(warehouseIds);
    }

    @Query("""
            select tx
            from InventoryTransaction tx
            where tx.type = com.toir.enums.InventoryTransactionType.ADJUSTMENT
            order by tx.transactionDate desc, tx.createdAt desc
            """)
    List<InventoryTransaction> findAllAdjustmentsForReconciliationAdmin();

    @Query("""
            select tx
            from InventoryTransaction tx
            where tx.type = com.toir.enums.InventoryTransactionType.ADJUSTMENT
              and tx.warehouseId in :warehouseIds
            order by tx.transactionDate desc, tx.createdAt desc
            """)
    List<InventoryTransaction> findAllAdjustmentsForReconciliationScoped(
            @Param("warehouseIds") Collection<UUID> warehouseIds
    );
}
