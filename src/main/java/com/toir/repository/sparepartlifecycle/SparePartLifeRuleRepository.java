package com.toir.repository.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartLifeRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SparePartLifeRuleRepository extends JpaRepository<SparePartLifeRule, UUID>,
        JpaSpecificationExecutor<SparePartLifeRule> {

    Optional<SparePartLifeRule> findByIdAndIsDeletedFalse(UUID id);

    List<SparePartLifeRule> findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(UUID sparePartId);

    List<SparePartLifeRule> findAllBySparePartIdAndIsDeletedFalse(UUID sparePartId);
}
