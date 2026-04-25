package com.toir.config;

import com.toir.util.PaginationUtils;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Wraps any {@code List<*>} response from {@code @RestController} methods into
 * Spring's {@link Page} response shape.
 *
 * Page/Map/Set/single-object responses pass through unchanged.
 */
@RestControllerAdvice
public class ListPaginationAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> type = returnType.getParameterType();
        return List.class.isAssignableFrom(type);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        if (body instanceof List<?> list) {
            var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            int page = parseInt(params.getFirst("page"), 0);
            int requestedSize = parseInt(params.getFirst("pageSize"), parseInt(params.getFirst("size"), 0));
            int pageSize = PaginationUtils.pageSizeFromList(requestedSize, list.size());
            return PaginationUtils.page(list, page, pageSize, list.size());
        }
        return body;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
