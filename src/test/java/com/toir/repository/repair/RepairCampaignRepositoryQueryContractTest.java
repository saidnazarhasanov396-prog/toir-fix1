package com.toir.repository.repair;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCampaignRepositoryQueryContractTest {

    @Test
    void campaignMutationReadUsesPessimisticWriteLock() throws Exception {
        Method method = RepairCampaignRepository.class.getMethod(
                "findLockedByIdAndIsDeletedFalse", java.util.UUID.class);

        Lock lock = method.getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void filteredListOrdersNewestCampaignsFirst() {
        Method method = Arrays.stream(RepairCampaignRepository.class.getMethods())
                .filter(m -> m.getName().equals("findAllFiltered"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("ORDER BY created_at DESC, updated_at DESC");
    }
}
