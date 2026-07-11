package com.toir.repository.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartLifeLimit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SparePartLifeLimitRepository extends JpaRepository<SparePartLifeLimit, UUID> {

    List<SparePartLifeLimit> findAllByRuleIdAndIsDeletedFalseOrderBySequenceAsc(UUID ruleId);

    List<SparePartLifeLimit> findAllByRuleIdInAndIsDeletedFalseOrderBySequenceAsc(Collection<UUID> ruleIds);
}
