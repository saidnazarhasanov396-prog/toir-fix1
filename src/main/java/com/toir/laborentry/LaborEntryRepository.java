package com.toir.laborentry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LaborEntryRepository extends JpaRepository<LaborEntry, UUID> {
    List<LaborEntry> findAllByWorkOrderIdOrderByWorkDateAsc(UUID workOrderId);
}
