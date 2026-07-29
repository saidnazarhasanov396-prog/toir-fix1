package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface MaintenanceScheduleCalculationItemRepository
        extends Repository<MaintenanceScheduleCalculationItem, UUID> {

    <S extends MaintenanceScheduleCalculationItem> List<S> saveAll(
            Iterable<S> entities);

    @Query("""
            select item
            from MaintenanceScheduleCalculationItem item
            where item.plan.id = :planId
              and item.calculationRevision = :calculationRevision
            order by item.sourceItemKey asc
            """)
    List<MaintenanceScheduleCalculationItem>
            findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                    @Param("planId") UUID planId,
                    @Param("calculationRevision") long calculationRevision
            );

    @Query("""
            select count(item)
            from MaintenanceScheduleCalculationItem item
            where item.plan.id = :planId
              and item.calculationRevision = :calculationRevision
            """)
    long countByPlanIdAndCalculationRevision(
            @Param("planId") UUID planId,
            @Param("calculationRevision") long calculationRevision
    );

    @Query("""
            select case when count(item) > 0 then true else false end
            from MaintenanceScheduleCalculationItem item
            where item.plan.id = :planId
              and item.calculationRevision = :calculationRevision
              and item.sourceItemKey = :sourceItemKey
            """)
    boolean existsByPlanIdAndCalculationRevisionAndSourceItemKey(
            @Param("planId") UUID planId,
            @Param("calculationRevision") long calculationRevision,
            @Param("sourceItemKey") String sourceItemKey
    );
}
