package com.toir.repository.users;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryQueryContractTest {

    @Test
    void searchIdsQueryMustRemainCaseInsensitiveAndSearchByExpectedFields() {
        Method method = Arrays.stream(UserRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchIds"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("u.isDeleted = false");
        assertThat(query.value()).contains("u.fullName");
        assertThat(query.value()).contains("u.username");
        assertThat(query.value()).contains("u.email");
        assertThat(query.value()).contains("u.phone");
        assertThat(query.value()).contains("LOWER(");
    }
}
