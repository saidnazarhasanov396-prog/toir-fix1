package com.toir.service.faktura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.config.FakturaUzProperties;
import com.toir.dto.faktura.integration.FakturaUzAuthResponse;
import com.toir.dto.faktura.integration.FakturaUzDocumentsContentResponse;
import com.toir.dto.faktura.integration.FakturaUzDocumentsResponse;
import com.toir.dto.faktura.integration.FakturaUzGetDocumentsContentRequest;
import com.toir.dto.faktura.integration.FakturaUzGetDocumentsRequest;
import com.toir.dto.faktura.integration.FakturaUzUserDetailsResponse;
import com.toir.exception.RestException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class FakturaUzClientService {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final FakturaUzProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FakturaUzAuthResponse getAuthToken(String login, String password, String clientId, String clientSecret) {
        String body = form(Map.of(
                "grant_type", "password",
                "username", login,
                "password", password,
                "client_id", clientId,
                "client_secret", clientSecret
        ));
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEndpoints().getAuth()))
                .timeout(DEFAULT_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request, FakturaUzAuthResponse.class, "FakturaUz auth failed");
    }

    public FakturaUzDocumentsResponse getDocuments(FakturaUzGetDocumentsRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getEndpoints().getGetDocuments());
        if (request.getLimit() != null) {
            builder.queryParam("limit", request.getLimit());
        }
        if (request.getSkip() != null) {
            builder.queryParam("skip", request.getSkip());
        }
        if (request.getIsInbox() != null) {
            builder.queryParam("IsInbox", request.getIsInbox());
        }
        if (request.getCreatedFrom() != null) {
            builder.queryParam("CreatedFrom", request.getCreatedFrom());
        }
        if (request.getCreatedTo() != null) {
            builder.queryParam("CreatedTo", request.getCreatedTo());
        }
        if (request.getOrganizationInn() != null && !request.getOrganizationInn().isBlank()) {
            builder.queryParam("CompanyInn", request.getOrganizationInn());
        }
        addRepeated(builder, "types", request.getTypes());
        if ((request.getTypes() == null || request.getTypes().isEmpty()) && request.getType() != null) {
            builder.queryParam("types", request.getType());
        }
        addRepeated(builder, "Statuses", request.getStatuses());
        if ((request.getStatuses() == null || request.getStatuses().isEmpty()) && request.getStatus() != null) {
            builder.queryParam("Statuses", request.getStatus());
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(builder.build(true).toUri())
                .timeout(DEFAULT_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + request.getAuthToken())
                .GET()
                .build();
        return send(httpRequest, FakturaUzDocumentsResponse.class, "FakturaUz documents fetch failed");
    }

    public FakturaUzDocumentsContentResponse getDocumentsContent(FakturaUzGetDocumentsContentRequest request) {
        if (request == null || request.getDocumentUniqueIds() == null || request.getDocumentUniqueIds().isEmpty()) {
            return new FakturaUzDocumentsContentResponse(true, List.of());
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getEndpoints().getGetDocumentsContent());
        if (request.getCompanyInn() != null && !request.getCompanyInn().isBlank()) {
            builder.queryParam("CompanyInn", request.getCompanyInn());
        }
        if (request.getIsDeserialized() != null) {
            builder.queryParam("isDeserialized", request.getIsDeserialized());
        }
        String body = writeJson(Map.of("DocumentUniqueIds", request.getDocumentUniqueIds()));
        HttpRequest httpRequest = HttpRequest.newBuilder(builder.build(true).toUri())
                .timeout(DEFAULT_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + request.getAuthToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(httpRequest, FakturaUzDocumentsContentResponse.class, "FakturaUz content fetch failed");
    }

    public FakturaUzUserDetailsResponse getUserDetails(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEndpoints().getGetUserDetails()))
                .timeout(DEFAULT_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        FakturaUzUserDetailsResponse response = send(request, FakturaUzUserDetailsResponse.class, "FakturaUz user details fetch failed");
        if (response == null || response.getCompanies() == null || response.getCompanies().isEmpty()) {
            throw RestException.notFound("No company returned by FakturaUz for provided credentials");
        }
        return response;
    }

    public String getCompanyInn(String accessToken) {
        return getUserDetails(accessToken).getCompanies().getFirst().getInn();
    }

    private <T> T send(HttpRequest request, Class<T> type, String errorPrefix) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw RestException.badRequest(errorPrefix + ". HTTP " + response.statusCode() + ": " + truncate(response.body()));
            }
            if (response.body() == null || response.body().isBlank()) {
                return null;
            }
            return objectMapper.readValue(response.body(), type);
        } catch (RestException e) {
            throw e;
        } catch (Exception e) {
            throw RestException.badRequest(errorPrefix + ": " + e.getMessage());
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw RestException.badRequest("Failed to serialize FakturaUz request: " + e.getMessage());
        }
    }

    private String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void addRepeated(UriComponentsBuilder builder, String name, List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (Integer value : values) {
            builder.queryParam(name, value);
        }
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 1000 ? body : body.substring(0, 1000);
    }
}
