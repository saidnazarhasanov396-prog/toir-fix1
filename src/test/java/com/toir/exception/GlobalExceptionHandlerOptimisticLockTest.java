package com.toir.exception;

import com.toir.entity.repair.RepairCampaign;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerOptimisticLockTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void springOptimisticLockFailureHasStableConflictContract() {
        var request = request("/api/v1/repair-campaigns/" + UUID.randomUUID());
        OptimisticLockingFailureException failure = new ObjectOptimisticLockingFailureException(
                RepairCampaign.class, UUID.randomUUID());

        var response = handler.handleOptimisticLock(failure, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("Resource was modified by another request; reload and retry");
    }

    @Test
    void jpaOptimisticLockFailureHasStableConflictContract() {
        var request = request("/api/v1/repair-campaigns/" + UUID.randomUUID());

        var response = handler.handleOptimisticLock(new OptimisticLockException("database detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("Resource was modified by another request; reload and retry");
    }

    private jakarta.servlet.http.HttpServletRequest request(String uri) {
        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }
}
