package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.LaborEntry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface LaborEntryRepository extends JpaRepository<LaborEntry, UUID> {
    @Query(value = "SELECT * FROM labor_entries WHERE work_order_id = :workOrderId AND is_deleted = false ORDER BY work_date ASC", nativeQuery = true)
    List<LaborEntry> findAllByWorkOrderIdOrderByWorkDateAsc(@Param("workOrderId") UUID workOrderId);
}
