package com.toir.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Single localization boundary for every HTTP error returned by the backend. */
public final class BackendErrorLocalizer {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final Locale UZ = Locale.forLanguageTag("uz");
    private static final List<Locale> SUPPORTED = List.of(RU, UZ, Locale.ENGLISH);

    private static final Map<String, Translation> TRANSLATIONS = Map.ofEntries(
            entry("BAD_REQUEST", "Invalid request", "Некорректный запрос", "Noto‘g‘ri so‘rov"),
            entry("AUTHENTICATION_REQUIRED", "Authentication is required", "Требуется авторизация", "Avtorizatsiya talab qilinadi"),
            entry("INVALID_CREDENTIALS", "Invalid credentials", "Неверный логин или пароль", "Login yoki parol noto‘g‘ri"),
            entry("ACCESS_DENIED", "Access denied", "Доступ запрещён", "Kirish taqiqlangan"),
            entry("RESOURCE_NOT_FOUND", "Resource not found", "Ресурс не найден", "Resurs topilmadi"),
            entry("RESOURCE_CONFLICT", "The operation conflicts with the current resource state", "Операция конфликтует с текущим состоянием ресурса", "Amal resursning joriy holatiga zid"),
            entry("OPTIMISTIC_LOCK", "Resource was modified by another request; reload and retry", "Ресурс был изменён другим запросом; обновите данные и повторите попытку", "Resurs boshqa so‘rov orqali o‘zgartirildi; ma’lumotlarni yangilang va qayta urinib ko‘ring"),
            entry("VALIDATION_FAILED", "Validation failed", "Проверка данных не пройдена", "Ma’lumotlar tekshiruvdan o‘tmadi"),
            entry("INVALID_PARAMETER_VALUE", "A request parameter has an invalid value", "Параметр запроса содержит недопустимое значение", "So‘rov parametrida noto‘g‘ri qiymat bor"),
            entry("INVALID_REQUEST_BODY", "Invalid request body", "Некорректное тело запроса", "So‘rov tanasi noto‘g‘ri"),
            entry("METHOD_NOT_ALLOWED", "HTTP method is not allowed", "HTTP-метод не поддерживается", "HTTP usuliga ruxsat berilmagan"),
            entry("MISSING_REQUEST_PARAMETER", "A required request parameter is missing", "Отсутствует обязательный параметр запроса", "So‘rovning majburiy parametri ko‘rsatilmagan"),
            entry("REJECTION_COMMENT_REQUIRED", "Rejection comment is required", "Укажите причину отклонения", "Rad etish sababini ko‘rsating"),
            entry("UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", "Неподдерживаемый формат данных", "Ma’lumot formati qo‘llab-quvvatlanmaydi"),
            entry("INTERNAL_SERVER_ERROR", "Unexpected server error", "Внутренняя ошибка сервера", "Serverning ichki xatosi"),
            entry("HTTP_ERROR", "Request failed", "Не удалось выполнить запрос", "So‘rovni bajarib bo‘lmadi")
    );

    private BackendErrorLocalizer() {
    }

    public static LocalizedError localize(
            HttpServletRequest request,
            HttpStatus status,
            String requestedCode,
            Map<String, ?> requestedParams,
            String legacyEnglishMessage,
            boolean allowLegacyEnglish
    ) {
        Locale locale = resolveLocale(request == null ? null : request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
        String code = requestedCode == null || requestedCode.isBlank() ? defaultCode(status) : requestedCode;
        Map<String, Object> params = immutableParams(requestedParams);

        if ("LIFETIME_EXPIRED".equals(code)) {
            return new LocalizedError(code, params, lifetimeExpired(locale, params));
        }

        Translation translation = TRANSLATIONS.get(code);
        if (translation == null) {
            translation = TRANSLATIONS.get(defaultCode(status));
        }
        if (translation == null) {
            translation = TRANSLATIONS.get("HTTP_ERROR");
        }

        String message = allowLegacyEnglish
                && Locale.ENGLISH.getLanguage().equals(locale.getLanguage())
                && legacyEnglishMessage != null
                && !legacyEnglishMessage.isBlank()
                ? legacyEnglishMessage
                : translation.forLocale(locale);
        return new LocalizedError(code, params, message);
    }

    public static Locale resolveLocale(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Locale.ENGLISH;
        }
        try {
            Locale match = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED);
            return match == null ? Locale.ENGLISH : match;
        } catch (IllegalArgumentException ignored) {
            return Locale.ENGLISH;
        }
    }

    public static String defaultCode(HttpStatus status) {
        String known = switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "AUTHENTICATION_REQUIRED";
            case FORBIDDEN -> "ACCESS_DENIED";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case CONFLICT -> "RESOURCE_CONFLICT";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case UNPROCESSABLE_ENTITY -> "VALIDATION_FAILED";
            case INTERNAL_SERVER_ERROR -> "INTERNAL_SERVER_ERROR";
            default -> null;
        };
        if (known != null) {
            return known;
        }
        if (status.is4xxClientError()) {
            return "BAD_REQUEST";
        }
        if (status.is5xxServerError()) {
            return "INTERNAL_SERVER_ERROR";
        }
        return "HTTP_ERROR";
    }

    private static String lifetimeExpired(Locale locale, Map<String, Object> params) {
        long years = number(params.get("years"));
        long months = number(params.get("months"));
        if (RU.getLanguage().equals(locale.getLanguage())) {
            return "Срок службы истёк " + years + " " + russianUnit(years, "год", "года", "лет")
                    + " " + months + " " + russianUnit(months, "месяц", "месяца", "месяцев") + " назад";
        }
        if (UZ.getLanguage().equals(locale.getLanguage())) {
            return "Xizmat muddati " + years + " yil " + months + " oy oldin tugagan";
        }
        return "Lifetime expired " + years + " " + englishUnit(years, "year")
                + " " + months + " " + englishUnit(months, "month") + " ago";
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String englishUnit(long value, String singular) {
        return Math.abs(value) == 1 ? singular : singular + "s";
    }

    private static String russianUnit(long value, String one, String few, String many) {
        long absolute = Math.abs(value) % 100;
        if (absolute >= 11 && absolute <= 14) {
            return many;
        }
        return switch ((int) (absolute % 10)) {
            case 1 -> one;
            case 2, 3, 4 -> few;
            default -> many;
        };
    }

    private static Map<String, Object> immutableParams(Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        params.forEach(copy::put);
        return Collections.unmodifiableMap(copy);
    }

    private static Map.Entry<String, Translation> entry(String code, String en, String ru, String uz) {
        return Map.entry(code, new Translation(en, ru, uz));
    }

    private record Translation(String en, String ru, String uz) {
        private String forLocale(Locale locale) {
            return switch (locale.getLanguage()) {
                case "ru" -> ru;
                case "uz" -> uz;
                default -> en;
            };
        }
    }

    public record LocalizedError(String errorCode, Map<String, Object> params, String message) {
    }
}
