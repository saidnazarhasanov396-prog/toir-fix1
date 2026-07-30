package com.toir.repository.planning;

import com.toir.entity.planning.PprPlanningVariantItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PprPlanningVariantItemRepository
        extends Repository<PprPlanningVariantItem, UUID> {

    <S extends PprPlanningVariantItem> S save(S item);

    <S extends PprPlanningVariantItem> List<S> saveAll(Iterable<S> items);

    @Query("""
            select item
            from PprPlanningVariantItem item
            where item.variant.id = :variantId
              and item.revision = :revision
            order by item.sourceItemKey asc
            """)
    List<PprPlanningVariantItem> findAllByVariantIdAndRevisionOrderBySourceItemKey(
            @Param("variantId") UUID variantId,
            @Param("revision") long revision);
}
