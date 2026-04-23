package com.toir.repository;
import com.toir.entity.LaborEntry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LaborEntryRepository extends JpaRepository<LaborEntry, UUID> {
    List<LaborEntry> findAllByWorkOrderIdOrderByWorkDateAsc(UUID workOrderId);
}
