package com.toir.rootcause;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RootCauseRepository extends JpaRepository<RootCause, UUID> {
    boolean existsByCode(String code);
}
