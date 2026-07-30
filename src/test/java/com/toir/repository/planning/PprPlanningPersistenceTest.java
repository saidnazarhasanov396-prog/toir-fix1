package com.toir.repository.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toir.entity.planning.PprPlanningSession;
import com.toir.entity.planning.PprPlanningVariant;
import com.toir.entity.planning.PprPlanningVariantItem;
import com.toir.enums.planning.PprPlanningSessionStatus;
import com.toir.enums.planning.PprPlanningVariantStatus;
import com.toir.test.RepositorySliceTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@RepositorySliceTest
class PprPlanningPersistenceTest {

    @Autowired
    PprPlanningSessionRepository sessionRepository;

    @Autowired
    PprPlanningVariantRepository variantRepository;

    @Autowired
    PprPlanningVariantItemRepository itemRepository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void oneSessionPersistsMultipleNamedVariants() {
        PprPlanningSession session = sessionRepository.save(session("Annual 2027"));

        variantRepository.save(variant(session, "Base"));
        variantRepository.save(variant(session, "Reduced downtime"));
        entityManager.flush();
        entityManager.clear();

        assertThat(variantRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(session.getId()))
                .extracting(PprPlanningVariant::getName)
                .containsExactly("Base", "Reduced downtime");
    }

    @Test
    void normalizedVariantNameMustBeUniqueWithinSession() {
        PprPlanningSession session = sessionRepository.save(session("Annual 2027"));
        variantRepository.saveAndFlush(variant(session, "Base"));

        assertThatThrownBy(() -> variantRepository.saveAndFlush(variant(session, "  BASE  ")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotItemsAreReadByExactVariantRevision() {
        PprPlanningSession session = sessionRepository.save(session("Annual 2027"));
        PprPlanningVariant variant = variantRepository.save(variant(session, "Base"));
        itemRepository.save(item(variant, 1, "a".repeat(64), LocalDate.of(2027, 2, 1)));
        itemRepository.save(item(variant, 2, "b".repeat(64), LocalDate.of(2027, 3, 1)));
        entityManager.flush();
        entityManager.clear();

        assertThat(itemRepository.findAllByVariantIdAndRevisionOrderBySourceItemKey(
                variant.getId(), 1))
                .singleElement()
                .extracting(PprPlanningVariantItem::getPlannedDate)
                .isEqualTo(LocalDate.of(2027, 2, 1));
    }

    private static PprPlanningSession session(String name) {
        PprPlanningSession session = new PprPlanningSession();
        session.setName(name);
        session.setYear(2027);
        session.setDepartmentId(UUID.randomUUID());
        session.setStartDate(LocalDate.of(2027, 1, 1));
        session.setEndDate(LocalDate.of(2027, 12, 31));
        session.setStatus(PprPlanningSessionStatus.DRAFT);
        return session;
    }

    private static PprPlanningVariant variant(PprPlanningSession session, String name) {
        PprPlanningVariant variant = new PprPlanningVariant();
        variant.setSession(session);
        variant.setName(name);
        variant.setRevision(0);
        variant.setHashVersion(1);
        variant.setStatus(PprPlanningVariantStatus.DRAFT);
        return variant;
    }

    private static PprPlanningVariantItem item(
            PprPlanningVariant variant,
            long revision,
            String sourceItemKey,
            LocalDate plannedDate) {
        PprPlanningVariantItem item = new PprPlanningVariantItem();
        item.setVariant(variant);
        item.setRevision(revision);
        item.setSourceItemKey(sourceItemKey);
        item.setSourceItemKeyVersion(1);
        item.setEquipmentId(UUID.randomUUID());
        item.setPlannedDate(plannedDate);
        item.setScheduledStart(plannedDate.atTime(8, 0));
        item.setScheduledEnd(LocalDateTime.of(plannedDate, java.time.LocalTime.of(10, 0)));
        item.setTaskTitleSnapshot("Maintenance " + plannedDate);
        item.setEquipmentCodeSnapshot("EQ-1");
        item.setEquipmentNameSnapshot("Pump");
        item.setWorkOrderLeadDays(7);
        return item;
    }
}
