package com.toir.repository;

import com.toir.entity.StockMovementFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementFileRepository extends JpaRepository<StockMovementFile, UUID> {

    @Query("""
            select smf
            from StockMovementFile smf
            join fetch smf.file f
            join fetch smf.movement sm
            where sm.id = :movementId
              and sm.isDeleted = false
              and f.deleted = false
            order by smf.sortOrder asc
            """)
    List<StockMovementFile> findActiveByMovementId(@Param("movementId") UUID movementId);

    @Query("""
            select smf
            from StockMovementFile smf
            join fetch smf.file f
            join fetch smf.movement sm
            where sm.id = :movementId
              and f.id = :fileId
              and sm.isDeleted = false
              and f.deleted = false
            """)
    Optional<StockMovementFile> findActiveByMovementIdAndFileId(
            @Param("movementId") UUID movementId,
            @Param("fileId") UUID fileId
    );

    @Query("""
            select count(smf)
            from StockMovementFile smf
            join smf.file f
            where smf.movement.id = :movementId
              and f.deleted = false
            """)
    long countByMovementId(@Param("movementId") UUID movementId);
}
