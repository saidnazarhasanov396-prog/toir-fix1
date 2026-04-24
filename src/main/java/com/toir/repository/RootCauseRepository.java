package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.RootCause;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface RootCauseRepository extends JpaRepository<RootCause, UUID> {
    boolean existsByCode(String code);
}
