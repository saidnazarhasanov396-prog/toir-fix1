package com.toir.repository;

import com.toir.entity.ApprovalDelegate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalDelegateRepository extends JpaRepository<ApprovalDelegate, UUID> {

    @Query(value = """
            select *
            from approval_delegates
            where is_deleted = false
              and approver_id = cast(:approverId as uuid)
              and delegate_id = cast(:delegateId as uuid)
              and active_from <= :now
              and (active_until is null or active_until >= :now)
            order by active_from desc
            limit 1
            """, nativeQuery = true)
    Optional<ApprovalDelegate> findActiveDelegation(@Param("approverId") UUID approverId,
                                                    @Param("delegateId") UUID delegateId,
                                                    @Param("now") Instant now);
}
