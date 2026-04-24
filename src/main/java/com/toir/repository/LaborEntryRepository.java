package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.LaborEntry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface LaborEntryRepository extends JpaRepository<LaborEntry, UUID> {
    List<LaborEntry> findAllByWorkOrderIdOrderByWorkDateAsc(UUID workOrderId);
}
