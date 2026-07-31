package com.toir.exception;

import com.toir.enums.ErrorType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

class GlobalExceptionHandlerLocalizationTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void localizesFrameworkErrorsFromAcceptLanguage() {
        MockHttpServletRequest request = request("ru-RU,ru;q=0.9,en;q=0.8");

        var response = handler.handleAccessDenied(new AccessDeniedException("sensitive detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Доступ запрещён");
        assertThat(response.getBody().errorCode()).isEqualTo("ACCESS_DENIED");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("ru");
    }

    @Test
    void localizesLegacyBusinessErrorsWithoutLeakingEnglishText() {
        MockHttpServletRequest request = request("uz");

        var response = handler.handleRestException(
                RestException.notFound("Work order not found: work-order-1"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body.message()).isEqualTo("Resurs topilmadi");
        assertThat(body.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(body.message()).doesNotContain("Work order");
    }

    @Test
    void returnsStructuredCodeParamsAndNaturalThreeLanguageMessage() {
        RestException exception = RestException.localized(
                HttpStatus.CONFLICT,
                "LIFETIME_EXPIRED",
                Map.of("years", 2, "months", 3)
        );

        ErrorResponse ru = (ErrorResponse) handler.handleRestException(exception, request("ru")).getBody();
        ErrorResponse uz = (ErrorResponse) handler.handleRestException(exception, request("uz")).getBody();
        ErrorResponse en = (ErrorResponse) handler.handleRestException(exception, request("en")).getBody();

        assertThat(ru.message()).isEqualTo("Срок службы истёк 2 года 3 месяца назад");
        assertThat(uz.message()).isEqualTo("Xizmat muddati 2 yil 3 oy oldin tugagan");
        assertThat(en.message()).isEqualTo("Lifetime expired 2 years 3 months ago");
        assertThat(ru.errorCode()).isEqualTo("LIFETIME_EXPIRED");
        assertThat(ru.params()).containsExactlyInAnyOrderEntriesOf(Map.of("years", 2, "months", 3));
    }

    @Test
    void promotesExistingTechnicalMessagesToStableErrorCodes() {
        ErrorResponse body = (ErrorResponse) handler.handleRestException(
                RestException.conflict("RESERVATION_DUPLICATE"),
                request("ru")
        ).getBody();

        assertThat(body.errorCode()).isEqualTo("RESERVATION_DUPLICATE");
        assertThat(body.message()).isEqualTo("Операция конфликтует с текущим состоянием ресурса");
        assertThat(body.params()).isEmpty();
    }

    @Test
    void localizesExistingTypedFileErrorsWithoutLosingTheirSpecificMeaning() {
        RestException exception = RestException.restThrow(ErrorType.FILE_TOO_LARGE);

        ErrorResponse ru = (ErrorResponse) handler.handleRestException(exception, request("ru")).getBody();
        ErrorResponse uz = (ErrorResponse) handler.handleRestException(exception, request("uz")).getBody();
        ErrorResponse en = (ErrorResponse) handler.handleRestException(exception, request("en")).getBody();

        assertThat(ru.errorCode()).isEqualTo("FILE_TOO_LARGE");
        assertThat(ru.message()).isEqualTo("Размер файла превышает допустимый предел");
        assertThat(uz.message()).isEqualTo("Fayl hajmi ruxsat etilgan chegaradan oshadi");
        assertThat(en.message()).isEqualTo("File size exceeds the allowed limit");
    }

    private MockHttpServletRequest request(String acceptLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        return request;
    }
}
