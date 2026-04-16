package com.toir.contractor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContractorRepository extends JpaRepository<Contractor, UUID> {
    boolean existsByCode(String code);
}
