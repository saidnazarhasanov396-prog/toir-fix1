package com.toir.sparepart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SparePartRepository extends JpaRepository<SparePart, UUID> {
    boolean existsByCode(String code);
}
