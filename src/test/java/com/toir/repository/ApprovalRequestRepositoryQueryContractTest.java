package com.toir.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalRequestRepositoryQueryContractTest {

    @Test
    void allPendingTargetActionQueryIsDeterministicAndIncludesLegacyAliases() throws Exception {
        Method method = ApprovalRequestRepository.class.getMethod(
                "findAllPendingByTargetAndAction",
                String.class,
                UUID.class,
                String.class,
                String.class);

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).contains(
                "is_deleted = false",
                "status = :status",
                "COALESCE(target_type, document_type) = :targetType",
                "COALESCE(target_id, document_id) = cast(:targetId as uuid)",
                "COALESCE(action_type, 'APPROVE') = :actionType",
                "ORDER BY created_at DESC, id DESC");
    }
}
