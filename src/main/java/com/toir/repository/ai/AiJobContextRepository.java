package com.toir.repository.ai;

import com.toir.entity.ai.AiJobContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiJobContextRepository extends JpaRepository<AiJobContext, UUID> {

    @Query("""
            select c from AiJobContext c
            where c.isDeleted = false
              and c.jobId = :jobId
            """)
    Optional<AiJobContext> findByJobIdAndIsDeletedFalse(@Param("jobId") UUID jobId);
}
