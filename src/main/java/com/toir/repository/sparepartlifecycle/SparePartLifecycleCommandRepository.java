package com.toir.repository.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartLifecycleCommand;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SparePartLifecycleCommandRepository extends JpaRepository<SparePartLifecycleCommand, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select command
            from SparePartLifecycleCommand command
            where command.idempotencyKey = :idempotencyKey
              and command.isDeleted = false
            """)
    Optional<SparePartLifecycleCommand> findByIdempotencyKeyForUpdate(
            @Param("idempotencyKey") String idempotencyKey
    );
}
