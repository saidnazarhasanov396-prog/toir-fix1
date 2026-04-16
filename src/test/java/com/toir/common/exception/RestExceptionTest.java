package com.toir.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionTest {

    @Test
    void factoryMethodsCarryCorrectStatus() {
        assertThat(RestException.badRequest("x").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(RestException.unauthorized("x").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(RestException.forbidden("x").getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(RestException.notFound("x").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(RestException.conflict("x").getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void carriesMessage() {
        RestException ex = RestException.notFound("Equipment 42 not found");
        assertThat(ex.getMessage()).isEqualTo("Equipment 42 not found");
    }
}
